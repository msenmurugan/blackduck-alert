/**
 * hub-alert
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
package com.blackducksoftware.integration.hub.alert.channel.slack;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.blackducksoftware.integration.hub.alert.audit.repository.AuditEntryRepositoryWrapper;
import com.blackducksoftware.integration.hub.alert.channel.ChannelRestFactory;
import com.blackducksoftware.integration.hub.alert.channel.DistributionChannel;
import com.blackducksoftware.integration.hub.alert.channel.SupportedChannels;
import com.blackducksoftware.integration.hub.alert.channel.slack.repository.distribution.SlackDistributionConfigEntity;
import com.blackducksoftware.integration.hub.alert.channel.slack.repository.distribution.SlackDistributionRepositoryWrapper;
import com.blackducksoftware.integration.hub.alert.channel.slack.repository.global.GlobalSlackConfigEntity;
import com.blackducksoftware.integration.hub.alert.config.GlobalProperties;
import com.blackducksoftware.integration.hub.alert.datasource.entity.repository.CommonDistributionRepositoryWrapper;
import com.blackducksoftware.integration.hub.alert.digest.model.CategoryData;
import com.blackducksoftware.integration.hub.alert.digest.model.ItemData;
import com.blackducksoftware.integration.hub.alert.digest.model.ProjectData;
import com.blackducksoftware.integration.hub.alert.digest.model.ProjectDataFactory;
import com.blackducksoftware.integration.hub.notification.processor.ItemTypeEnum;
import com.blackducksoftware.integration.hub.notification.processor.NotificationCategoryEnum;
import com.blackducksoftware.integration.hub.rest.exception.IntegrationRestException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.Request;

@Component
public class SlackChannel extends DistributionChannel<SlackEvent, GlobalSlackConfigEntity, SlackDistributionConfigEntity> {
    private final static Logger logger = LoggerFactory.getLogger(SlackChannel.class);

    private final GlobalProperties globalProperties;

    @Autowired
    public SlackChannel(final Gson gson, final AuditEntryRepositoryWrapper auditEntryRepository, final SlackDistributionRepositoryWrapper slackDistributionRepository, final CommonDistributionRepositoryWrapper commonDistributionRepository,
            final GlobalProperties globalProperties) {
        super(gson, auditEntryRepository, null, slackDistributionRepository, commonDistributionRepository, SlackEvent.class);
        this.globalProperties = globalProperties;
    }

    @Override
    public void sendMessage(final SlackEvent event, final SlackDistributionConfigEntity config) {
        final ProjectData projectData = event.getProjectData();
        final String htmlMessage = createMessage(projectData);
        try {
            sendMessage(htmlMessage, config);
            setAuditEntrySuccess(event.getAuditEntryId());
        } catch (final IntegrationException e) {
            setAuditEntryFailure(event.getAuditEntryId(), e.getMessage(), e);

            if (e instanceof IntegrationRestException) {
                logger.error(((IntegrationRestException) e).getHttpStatusCode() + ":" + ((IntegrationRestException) e).getHttpStatusMessage());
            }
            logger.error(e.getMessage(), e);
        } catch (final Exception e) {
            setAuditEntryFailure(event.getAuditEntryId(), e.getMessage(), e);
            logger.error(e.getMessage(), e);
        }
    }

    private String sendMessage(final String htmlMessage, final SlackDistributionConfigEntity config) throws IntegrationException {
        final String slackUrl = config.getWebhook();
        final String jsonString = getJsonString(htmlMessage, config.getChannelName(), config.getChannelUsername());

        final Map<String, String> requestProperties = new HashMap<>();
        requestProperties.put("Content-Type", "application/json");

        final ChannelRestFactory channelRestFactory = new ChannelRestFactory(slackUrl, globalProperties, logger);
        final Request request = channelRestFactory.createRequest(slackUrl, jsonString, requestProperties);

        channelRestFactory.sendRequest(request);
        return "Succesfully sent Slack message!";
    }

    protected String createMessage(final ProjectData projectData) {
        final StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(projectData.getProjectName());
        messageBuilder.append(" > ");
        messageBuilder.append(projectData.getProjectVersion());
        messageBuilder.append(System.lineSeparator());

        final Map<NotificationCategoryEnum, CategoryData> categoryMap = projectData.getCategoryMap();
        if (categoryMap != null) {
            for (final NotificationCategoryEnum category : NotificationCategoryEnum.values()) {
                final CategoryData data = categoryMap.get(category);
                if (data != null) {
                    messageBuilder.append("- - - - - - - - - - - - - - - - - - - -");
                    messageBuilder.append(System.lineSeparator());
                    messageBuilder.append("Type: ");
                    messageBuilder.append(data.getCategoryKey());
                    messageBuilder.append(System.lineSeparator());
                    messageBuilder.append("Number of Changes: ");
                    messageBuilder.append(data.getItemCount());
                    for (final ItemData item : data.getItemList()) {
                        messageBuilder.append(System.lineSeparator());
                        final Map<String, Object> dataSet = item.getDataSet();
                        final String ruleKey = ItemTypeEnum.RULE.toString();
                        if (dataSet.containsKey(ruleKey) && StringUtils.isNotBlank(dataSet.get(ruleKey).toString())) {
                            messageBuilder.append("Rule: " + dataSet.get(ItemTypeEnum.RULE.toString()));
                            messageBuilder.append(System.lineSeparator());
                        }

                        if (dataSet.containsKey(ProjectDataFactory.VULNERABILITY_COUNT_KEY_ADDED)) {
                            final Number numericValue = (Number) dataSet.get(ProjectDataFactory.VULNERABILITY_COUNT_KEY_ADDED);
                            messageBuilder.append("Vulnerability Count Added: " + numericValue.intValue());
                            messageBuilder.append(System.lineSeparator());
                        }

                        if (dataSet.containsKey(ProjectDataFactory.VULNERABILITY_COUNT_KEY_UPDATED)) {
                            final Number numericValue = (Number) dataSet.get(ProjectDataFactory.VULNERABILITY_COUNT_KEY_UPDATED);
                            messageBuilder.append("Vulnerability Count Updated: " + numericValue.intValue());
                            messageBuilder.append(System.lineSeparator());
                        }

                        if (dataSet.containsKey(ProjectDataFactory.VULNERABILITY_COUNT_KEY_DELETED)) {
                            final Number numericValue = (Number) dataSet.get(ProjectDataFactory.VULNERABILITY_COUNT_KEY_DELETED);
                            messageBuilder.append("Vulnerability Count Deleted: " + numericValue.intValue());
                            messageBuilder.append(System.lineSeparator());
                        }

                        messageBuilder.append("Component: " + dataSet.get(ItemTypeEnum.COMPONENT.toString()));
                        messageBuilder.append(" [" + dataSet.get(ItemTypeEnum.VERSION.toString()) + "]");
                    }
                    messageBuilder.append(System.lineSeparator());
                }
            }
        } else {
            messageBuilder.append(" A notification was received, but it was empty.");
        }
        return messageBuilder.toString();
    }

    private String getJsonString(final String htmlMessage, final String channel, final String username) {
        final JsonObject json = new JsonObject();
        json.addProperty("text", htmlMessage);
        json.addProperty("channel", channel);
        json.addProperty("username", username);
        json.addProperty("mrkdwn", true);

        return json.toString();
    }

    @JmsListener(destination = SupportedChannels.SLACK)
    @Override
    public void receiveMessage(final String message) {
        super.receiveMessage(message);
    }

}
