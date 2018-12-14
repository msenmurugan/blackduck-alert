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

package com.synopsys.integration.alert.provider.blackduck.collector;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.jayway.jsonpath.TypeRef;
import com.synopsys.integration.alert.common.enumeration.ItemOperation;
import com.synopsys.integration.alert.common.exception.AlertException;
import com.synopsys.integration.alert.common.field.JsonField;
import com.synopsys.integration.alert.common.model.CategoryItem;
import com.synopsys.integration.alert.common.model.CategoryKey;
import com.synopsys.integration.alert.common.model.LinkableItem;
import com.synopsys.integration.alert.common.workflow.processor.MessageContentCollector;
import com.synopsys.integration.alert.common.workflow.processor.MessageContentProcessor;
import com.synopsys.integration.alert.database.entity.NotificationContent;
import com.synopsys.integration.alert.provider.blackduck.BlackDuckProviderContentTypes;
import com.synopsys.integration.alert.workflow.filter.field.JsonExtractor;
import com.synopsys.integration.alert.workflow.filter.field.JsonFieldAccessor;
import com.synopsys.integration.blackduck.api.generated.enumeration.NotificationType;
import com.synopsys.integration.blackduck.notification.content.ComponentVersionStatus;
import com.synopsys.integration.blackduck.notification.content.PolicyInfo;

@Component
@Scope("prototype")
public class BlackDuckPolicyMessageContentCollector extends MessageContentCollector {
    public static final String CATEGORY_TYPE = "policy";
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    public BlackDuckPolicyMessageContentCollector(final JsonExtractor jsonExtractor, final List<MessageContentProcessor> messageContentProcessorList) {
        super(jsonExtractor, messageContentProcessorList, Arrays.asList(BlackDuckProviderContentTypes.POLICY_OVERRIDE, BlackDuckProviderContentTypes.RULE_VIOLATION, BlackDuckProviderContentTypes.RULE_VIOLATION_CLEARED));
    }

    @Override
    protected void addCategoryItems(final List<CategoryItem> categoryItems, final JsonFieldAccessor jsonFieldAccessor, final List<JsonField<?>> notificationFields, final NotificationContent notificationContent) {
        if (NotificationType.POLICY_OVERRIDE.name().equals(notificationContent.getNotificationType())) {
            processPolicyOverride(categoryItems, jsonFieldAccessor, notificationFields, notificationContent);
        } else {
            processPolicy(categoryItems, jsonFieldAccessor, notificationFields, notificationContent);
        }
    }

    private void processPolicy(final List<CategoryItem> categoryItems, final JsonFieldAccessor jsonFieldAccessor, final List<JsonField<?>> notificationFields, final NotificationContent notificationContent) {
        final ItemOperation operation = getOperationFromNotification(notificationContent);
        if (operation == null) {
            return;
        }

        final List<JsonField<PolicyInfo>> policyFields = getFieldsOfType(notificationFields, new TypeRef<PolicyInfo>() {});
        final List<JsonField<ComponentVersionStatus>> componentFields = getFieldsOfType(notificationFields, new TypeRef<ComponentVersionStatus>() {});
        try {
            final List<PolicyInfo> policyItems = getFieldValueObjectsByLabel(jsonFieldAccessor, policyFields, BlackDuckProviderContentTypes.LABEL_POLICY_INFO_LIST);
            final List<ComponentVersionStatus> componentVersionStatuses = getFieldValueObjectsByLabel(jsonFieldAccessor, componentFields, BlackDuckProviderContentTypes.LABEL_COMPONENT_VERSION_STATUS);
            final Map<String, SortedSet<LinkableItem>> policyItemMap = mapPolicyToComponents(componentVersionStatuses);

            for (final PolicyInfo policyItem : policyItems) {
                final String policyUrl = policyItem.policy;
                final String policyName = policyItem.policyName;
                final LinkableItem policyLinkableItem = new LinkableItem(BlackDuckProviderContentTypes.LABEL_POLICY_NAME, policyName, policyUrl);
                if (policyItemMap.containsKey(policyUrl)) {
                    final SortedSet<LinkableItem> applicableItems = policyItemMap.get(policyUrl);
                    addApplicableItems(categoryItems, notificationContent.getId(), policyLinkableItem, policyUrl, operation, applicableItems);
                }
            }
        } catch (final AlertException ex) {
            logger.error("Mishandled the expected type of a notification field", ex);
        }
    }

    private Map<String, SortedSet<LinkableItem>> mapPolicyToComponents(final List<ComponentVersionStatus> componentVersionStatuses) {
        final Map<String, SortedSet<LinkableItem>> policyItemMap = new TreeMap<>();

        for (final ComponentVersionStatus versionStatus : componentVersionStatuses) {
            for (final String policyUrl : versionStatus.policies) {
                final SortedSet<LinkableItem> componentItems;
                if (policyItemMap.containsKey(policyUrl)) {
                    componentItems = policyItemMap.get(policyUrl);
                } else {
                    componentItems = new TreeSet<>();
                    policyItemMap.put(policyUrl, componentItems);
                }

                if (StringUtils.isNotBlank(versionStatus.componentName)) {
                    componentItems.add(new LinkableItem(BlackDuckProviderContentTypes.LABEL_COMPONENT_NAME, versionStatus.componentName, versionStatus.component));
                }

                if (StringUtils.isNotBlank(versionStatus.componentVersionName)) {
                    componentItems.add(new LinkableItem(BlackDuckProviderContentTypes.LABEL_COMPONENT_VERSION_NAME, versionStatus.componentVersionName, versionStatus.componentVersion));
                }
            }
        }
        return policyItemMap;
    }

    private void processPolicyOverride(final List<CategoryItem> categoryItems, final JsonFieldAccessor jsonFieldAccessor, final List<JsonField<?>> notificationFields, final NotificationContent notificationContent) {
        final ItemOperation operation = getOperationFromNotification(notificationContent);
        if (operation == null) {
            return;
        }

        final List<JsonField<String>> categoryFields = getStringFields(notificationFields);
        final List<LinkableItem> policyItems = getLinkableItemsByLabel(jsonFieldAccessor, categoryFields, BlackDuckProviderContentTypes.LABEL_POLICY_NAME);
        final List<LinkableItem> componentItems = getLinkableItemsByLabel(jsonFieldAccessor, categoryFields, BlackDuckProviderContentTypes.LABEL_COMPONENT_NAME);
        final List<LinkableItem> componentVersionItems = getLinkableItemsByLabel(jsonFieldAccessor, categoryFields, BlackDuckProviderContentTypes.LABEL_COMPONENT_VERSION_NAME);
        final Optional<LinkableItem> firstName = getLinkableItemsByLabel(jsonFieldAccessor, categoryFields, BlackDuckProviderContentTypes.LABEL_POLICY_OVERRIDE_FIRST_NAME).stream().findFirst();
        final Optional<LinkableItem> lastName = getLinkableItemsByLabel(jsonFieldAccessor, categoryFields, BlackDuckProviderContentTypes.LABEL_POLICY_OVERRIDE_LAST_NAME).stream().findFirst();
        final SortedSet<LinkableItem> applicableItems = new TreeSet<>();
        applicableItems.addAll(componentItems);
        applicableItems.addAll(componentVersionItems);

        if (firstName.isPresent() && lastName.isPresent()) {
            final String value = String.format("%s %s", firstName.get().getValue(), lastName.get().getValue());
            applicableItems.add(new LinkableItem(BlackDuckProviderContentTypes.LABEL_POLICY_OVERRIDE_BY, value));
        }

        for (final LinkableItem policyItem : policyItems) {
            final String policyUrl = policyItem.getUrl().orElse("");
            addApplicableItems(categoryItems, notificationContent.getId(), policyItem, policyUrl, operation, applicableItems);
        }
    }

    private ItemOperation getOperationFromNotification(final NotificationContent notificationContent) {
        final ItemOperation operation;
        final String notificationType = notificationContent.getNotificationType();
        if (NotificationType.RULE_VIOLATION_CLEARED.name().equals(notificationType)) {
            operation = ItemOperation.DELETE;
        } else if (NotificationType.RULE_VIOLATION.name().equals(notificationType)) {
            operation = ItemOperation.ADD;
        } else if (NotificationType.POLICY_OVERRIDE.name().equals(notificationType)) {
            operation = ItemOperation.DELETE;
        } else {
            operation = null;
            logger.error("Unrecognized notification type: The notification type '{}' is not valid for this collector.", notificationType);
        }

        return operation;
    }

    private void addApplicableItems(final List<CategoryItem> categoryItems, final Long notificationId, final LinkableItem policyItem, final String policyUrl, final ItemOperation operation, final SortedSet<LinkableItem> applicableItems) {
        final List<String> keyItems = applicableItems.stream().map(LinkableItem::getValue).collect(Collectors.toList());
        String[] keyArray = new String[keyItems.size() + 1];
        keyArray = keyItems.toArray(keyArray);
        keyArray[keyArray.length - 1] = policyUrl;
        final CategoryKey categoryKey = CategoryKey.from(CATEGORY_TYPE, keyArray);

        for (final LinkableItem item : applicableItems) {
            final SortedSet<LinkableItem> linkableItems;
            linkableItems = createLinkableItemSet(policyItem, item);
            addItem(categoryItems, new CategoryItem(categoryKey, operation, notificationId, linkableItems));
        }
    }
}