/**
 * alert-issuetracker
 *
 * Copyright (c) 2019 Synopsys, Inc.
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
package com.synopsys.integration.alert.issuetracker.jira.common;

import com.synopsys.integration.alert.common.action.TestAction;
import com.synopsys.integration.alert.common.message.model.MessageResult;
import com.synopsys.integration.alert.common.persistence.accessor.FieldAccessor;
import com.synopsys.integration.alert.issuetracker.exception.IssueTrackerException;
import com.synopsys.integration.exception.IntegrationException;

public abstract class JiraGlobalTestAction extends TestAction {
    protected abstract boolean isAppMissing(FieldAccessor fieldAccessor) throws IntegrationException;

    protected abstract boolean isUserMissing(FieldAccessor fieldAccessor) throws IntegrationException;

    protected abstract String getChannelDisplayName();

    @Override
    public MessageResult testConfig(String configId, String destination, FieldAccessor fieldAccessor) throws IntegrationException {
        try {
            if (isUserMissing(fieldAccessor)) {
                throw new IssueTrackerException("User did not match any known users.");
            }

            if (isAppMissing(fieldAccessor)) {
                throw new IssueTrackerException(String.format("Please configure the %s plugin for your server.", getChannelDisplayName()));
            }
        } catch (IntegrationException e) {
            throw new IssueTrackerException("An error occurred during testing: " + e.getMessage());
        }
        return new MessageResult(String.format("Successfully connected to %s instance.", getChannelDisplayName()));
    }
}
