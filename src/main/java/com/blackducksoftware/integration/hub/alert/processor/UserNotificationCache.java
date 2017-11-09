/**
 * Copyright (C) 2017 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
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
package com.blackducksoftware.integration.hub.alert.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.blackducksoftware.integration.hub.api.project.ProjectAssignmentRequestService;
import com.blackducksoftware.integration.hub.api.project.ProjectRequestService;
import com.blackducksoftware.integration.hub.dataservice.notification.model.NotificationContentItem;
import com.blackducksoftware.integration.hub.exception.HubIntegrationException;
import com.blackducksoftware.integration.hub.model.view.AssignedUserView;
import com.blackducksoftware.integration.hub.model.view.ProjectView;
import com.blackducksoftware.integration.hub.notification.processor.MapProcessorCache;
import com.blackducksoftware.integration.hub.notification.processor.NotificationCategoryEnum;
import com.blackducksoftware.integration.hub.notification.processor.event.NotificationEvent;

public class UserNotificationCache extends MapProcessorCache {
    private final Logger logger = LoggerFactory.getLogger(UserNotificationCache.class);
    private final ProjectRequestService projectRequestService;
    private final ProjectAssignmentRequestService projectAssignmentRequestService;

    public UserNotificationCache(final ProjectRequestService projectRequestService, final ProjectAssignmentRequestService projectAssignmentRequestService) {
        this.projectRequestService = projectRequestService;
        this.projectAssignmentRequestService = projectAssignmentRequestService;
    }

    @Override
    public Collection<NotificationEvent> getEvents() throws HubIntegrationException {
        return addUserInformation(super.getEvents());
    }

    public Collection<NotificationEvent> addUserInformation(final Collection<NotificationEvent> notificationEvents) {
        final List<NotificationEvent> userEventList = new ArrayList<>();

        final String key = "hub_user";
        notificationEvents.forEach(currentNotification -> {
            final NotificationContentItem notificationContentItem = (NotificationContentItem) currentNotification.getDataSet().get(NotificationEvent.DATA_SET_KEY_NOTIFICATION_CONTENT);
            final String projectLink = notificationContentItem.getProjectVersion().getProjectLink();
            final List<String> userNameList = getUserNames(projectLink);
            userNameList.forEach(userName -> {
                final String eventKey = currentNotification.getEventKey();
                final NotificationCategoryEnum categoryType = currentNotification.getCategoryType();
                final Map<String, Object> dataSet = new HashMap<>(currentNotification.getDataSet());
                dataSet.put(key, userName);
                final NotificationEvent userEvent = new NotificationEvent(eventKey, categoryType, dataSet);
                userEventList.add(userEvent);
            });
        });
        return userEventList;
    }

    private List<String> getUserNames(final String projectLink) {
        final List<String> userNameList = new ArrayList<>();
        try {
            final ProjectView projectView = projectRequestService.getItem(projectLink, ProjectView.class);
            final List<AssignedUserView> assignedUserList = projectAssignmentRequestService.getProjectUsers(projectView);
            if (!assignedUserList.isEmpty()) {
                assignedUserList.forEach(assignedUser -> {
                    userNameList.add(assignedUser.name);
                });
            }
        } catch (final IntegrationException ex) {
            logger.debug("Error getting the users for project {}", projectLink);
            logger.debug("Caused by:", ex);
        }

        return userNameList;
    }
}
