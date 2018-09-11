/**
 * blackduck-alert
 *
 * Copyright (C) 2018 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.synopsys.integration.alert.common.workflow.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.synopsys.integration.alert.common.descriptor.ProviderDescriptor;
import com.synopsys.integration.alert.common.enumeration.FieldContentIdentifier;
import com.synopsys.integration.alert.common.enumeration.FormatType;
import com.synopsys.integration.alert.common.field.HierarchicalField;
import com.synopsys.integration.alert.common.field.ObjectHierarchicalField;
import com.synopsys.integration.alert.common.model.CategoryItem;
import com.synopsys.integration.alert.common.model.LinkableItem;
import com.synopsys.integration.alert.common.model.TopicContent;
import com.synopsys.integration.alert.common.provider.ProviderContentType;
import com.synopsys.integration.alert.database.entity.NotificationContent;
import com.synopsys.integration.alert.workflow.filter.JsonExtractor;

public abstract class TopicCollector {
    private static final String DEFAULT_VALUE = "unknown";

    private final JsonExtractor jsonExtractor;
    private final ProviderDescriptor providerDescriptor;
    private final Collection<ProviderContentType> contentTypes;

    private final List<TopicContent> collectedContent;

    public TopicCollector(final JsonExtractor jsonExtractor, final ProviderDescriptor providerDescriptor) {
        this.jsonExtractor = jsonExtractor;
        this.providerDescriptor = providerDescriptor;
        this.contentTypes = providerDescriptor.getProviderContentTypes();

        this.collectedContent = new ArrayList<>();
    }

    public abstract void insert(final NotificationContent notification);

    public abstract List<TopicContent> collect(final FormatType format);

    protected final List<TopicContent> getCopyOfCollectedContent() {
        return Collections.unmodifiableList(collectedContent);
    }

    protected final void addContent(final TopicContent content) {
        collectedContent.add(content);
    }

    // TODO think about how to maintain order
    protected final List<TopicContent> getContentsOrCreateIfDoesNotExist(final NotificationContent notification) {
        final List<HierarchicalField> notificationFields = getFieldsForNotificationType(notification.getNotificationType());
        final String notificationJson = notification.getContent();

        final List<TopicContent> topicContentsForNotifications = new ArrayList<>();

        final List<LinkableItem> topicItems = getTopicItems(notificationFields, notificationJson);
        for (final LinkableItem topicItem : topicItems) {
            // FIXME get the correct subtopic for each topic
            String subTopicName = null;
            String subTopicValue = null;
            LinkableItem subTopic = null;
            if (hasSubTopic(notificationFields)) {
                subTopicName = getSubTopicName(notificationFields).orElse(DEFAULT_VALUE);
                subTopicValue = getSubTopicValue(notificationFields, notificationJson).orElse(DEFAULT_VALUE);
                final String subTopicUrl = getSubTopicUrl(notificationFields, notificationJson).orElse(null);

                subTopic = new LinkableItem(subTopicName, subTopicValue, subTopicUrl);
            }

            final TopicContent foundContent = findTopicContent(topicItem.getName(), topicItem.getValue(), subTopicName, subTopicValue);
            if (foundContent != null) {
                topicContentsForNotifications.add(foundContent);
            }
            final List<CategoryItem> categoryList = new ArrayList<>();
            topicContentsForNotifications.add(new TopicContent(topicItem.getName(), topicItem.getValue(), topicItem.getUrl().orElse(null), subTopic, categoryList));
        }
        return topicContentsForNotifications;
    }

    protected final List<LinkableItem> getTopicItems(final List<HierarchicalField> notificationFields, final String notificationJson) {
        final Optional<HierarchicalField> topicField = getFieldForContentIdentifier(notificationFields, FieldContentIdentifier.TOPIC);
        if (!topicField.isPresent()) {
            throw new IllegalStateException(String.format("The notification provided did not contain the required field: ", FieldContentIdentifier.TOPIC));
        }
        final Optional<HierarchicalField> topicUrlField = getFieldForContentIdentifier(notificationFields, FieldContentIdentifier.TOPIC_URL);
        return jsonExtractor.getLinkableItemsFromJson(topicField.get(), topicUrlField.orElse(null), notificationJson);
        // TODO if the JsonExtractor works correctly, we don't need the rest of this code
        //        final String topicName = topicField.getFieldKey();
        //        final List<String> topicValues = getFieldValues(topicField, notificationJson);
        //        final HierarchicalField topicUrlField = getFieldForContentIdentifier(notificationFields, FieldContentIdentifier.TOPIC + HierarchicalField.LABEL_URL_SUFFIX);
        //        List<String> topicUrlValues = Collections.emptyList();
        //        if (topicUrlField != null) {
        //            topicUrlValues = getFieldValues(topicUrlField, notificationJson);
        //        }
        //
        //        final List<LinkableItem> topicItems = new ArrayList<>();
        //        for (int i = 0; i < topicValues.size(); i++) {
        //            if (topicUrlValues.isEmpty()) {
        //                topicItems.add(new LinkableItem(topicName, topicValues.get(i)));
        //            } else {
        //                // TODO make sure that topicUrlValues is the same size as topicValues if it is not empty
        //                topicItems.add(new LinkableItem(topicName, topicValues.get(i), topicUrlValues.get(i)));
        //            }
        //        }
        //        return topicItems;
    }

    protected final boolean hasSubTopic(final List<HierarchicalField> notificationFields) {
        return notificationFields
                   .stream()
                   .anyMatch(field -> FieldContentIdentifier.SUB_TOPIC.equals(field.getContentIdentifier()));
    }

    protected final Optional<String> getSubTopicName(final List<HierarchicalField> notificationFields) {
        final Optional<HierarchicalField> subTopicField = getFieldForContentIdentifier(notificationFields, FieldContentIdentifier.SUB_TOPIC);
        if (subTopicField.isPresent()) {
            return Optional.of(subTopicField.get().getFieldKey());
        }
        return Optional.empty();
    }

    protected final Optional<String> getSubTopicValue(final List<HierarchicalField> notificationFields, final String notificationJson) {
        final Optional<HierarchicalField> subTopicField = getFieldForContentIdentifier(notificationFields, FieldContentIdentifier.SUB_TOPIC);
        return getOptionalFieldValue(subTopicField, notificationJson);
    }

    protected final Optional<String> getSubTopicUrl(final List<HierarchicalField> notificationFields, final String notificationJson) {
        final Optional<HierarchicalField> subTopicUrlField = getFieldForContentIdentifier(notificationFields, FieldContentIdentifier.SUB_TOPIC_URL);
        return getOptionalFieldValue(subTopicUrlField, notificationJson);
    }

    protected final List<HierarchicalField> getFieldsForNotificationType(final String notificationType) {
        for (final ProviderContentType providerContentType : contentTypes) {
            if (providerContentType.getNotificationType().equals(notificationType)) {
                return providerContentType.getNotificationFields();
            }
        }
        throw new IllegalArgumentException(String.format("No such notification type '%s' for provider: %s", notificationType, providerDescriptor.getName()));
    }

    protected final Map<String, HierarchicalField> getCategoryFieldMap(final String notificationType) {
        final List<HierarchicalField> notificationFields = getFieldsForNotificationType(notificationType);
        return notificationFields
                   .parallelStream()
                   .filter(field -> field.getContentIdentifier().equals(FieldContentIdentifier.CATEGORY_ITEM))
                   .collect(Collectors.toMap(HierarchicalField::getLabel, Function.identity()));
    }

    protected void addItem(final List<CategoryItem> categoryItems, final CategoryItem newItem) {
        final Optional<CategoryItem> foundItem = categoryItems.stream().filter(item -> item.getCategoryKey().equals(newItem.getCategoryKey())).findFirst();
        if (foundItem.isPresent()) {
            final CategoryItem categoryItem = foundItem.get();
            categoryItem.getItemList().addAll(newItem.getItemList());
        } else {
            categoryItems.add(newItem);
        }
    }

    protected final Optional<HierarchicalField> getFieldForContentIdentifier(final List<HierarchicalField> fields, final FieldContentIdentifier contentIdentifier) {
        return fields.stream().filter(field -> field.getContentIdentifier().equals(contentIdentifier)).findFirst();
    }

    protected final List<String> getFieldValues(final HierarchicalField field, final String notificationJson) {
        return jsonExtractor.getValuesFromJson(field, notificationJson);
    }

    protected final <T> List<T> getFieldValuesFromObject(final ObjectHierarchicalField field, final String notificationJson) {
        return jsonExtractor.getObjectFromJson(field, notificationJson);
    }

    protected List<String> getFieldValuesByLabel(final Map<String, HierarchicalField> categoryFields, final String label, final String notificationJson) {
        final HierarchicalField field = categoryFields.get(label);
        return getFieldValues(field, notificationJson);
    }

    protected <T> List<T> getFieldValuesObjectByLabel(final Map<String, HierarchicalField> categoryFields, final String label, final String notificationJson) {
        final HierarchicalField field = categoryFields.get(label);
        final Class<ObjectHierarchicalField> expected = ObjectHierarchicalField.class;
        if (expected.isAssignableFrom(field.getClass())) {
            return getFieldValuesFromObject((ObjectHierarchicalField) field, notificationJson);
        } else {
            // TODO throw an exception or handle this case more appropriately.
            return Collections.emptyList();
        }
    }

    protected final String getRequiredFieldValue(final HierarchicalField field, final String notificationJson) {
        final Optional<String> value = jsonExtractor.getFirstValueFromJson(field, notificationJson);
        if (value.isPresent()) {
            return value.get();
        }
        throw new IllegalStateException(String.format("The required field did not contain a value: ", field));
    }

    protected final Optional<String> getOptionalFieldValue(final Optional<HierarchicalField> field, final String notificationJson) {
        if (field.isPresent()) {
            return jsonExtractor.getFirstValueFromJson(field.get(), notificationJson);
        }
        return Optional.empty();
    }

    protected final Optional<HierarchicalField> getRelatedUrlField(final HierarchicalField relatedField, final List<HierarchicalField> categoryFields) {
        return categoryFields
                   .parallelStream()
                   .filter(field -> field.getLabel().equals(relatedField.getLabel() + HierarchicalField.LABEL_URL_SUFFIX))
                   .findFirst();
    }

    // TODO create TopicKey class
    private final TopicContent findTopicContent(final String topicName, final String topicValue, final String subTopicName, final String subTopicValue) {
        for (final TopicContent contentItem : collectedContent) {
            if (contentItem.getName().equals(topicName) && contentItem.getValue().equals(topicValue)) {
                if (contentItem.getSubTopic().isPresent()) {
                    final LinkableItem subTopicItem = contentItem.getSubTopic().get();
                    if (subTopicName != null && subTopicName.equals(subTopicItem.getName()) && subTopicValue != null && subTopicValue.equals(subTopicItem.getValue())) {
                        return contentItem;
                    }
                } else if (subTopicName == null && subTopicValue == null) {
                    return contentItem;
                }
            }
        }
        return null;
    }
}
