/**
 * blackduck-alert
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
package com.synopsys.integration.alert.channel.email.web;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.synopsys.integration.alert.channel.email.descriptor.EmailDescriptor;
import com.synopsys.integration.alert.common.action.CustomEndpointManager;
import com.synopsys.integration.alert.common.descriptor.config.field.LabelValueSelectOption;
import com.synopsys.integration.alert.common.descriptor.config.ui.ChannelDistributionUIConfig;
import com.synopsys.integration.alert.common.exception.AlertException;
import com.synopsys.integration.alert.common.persistence.accessor.ProviderDataAccessor;
import com.synopsys.integration.alert.common.persistence.model.ProviderUserModel;
import com.synopsys.integration.alert.common.rest.model.FieldValueModel;
import com.synopsys.integration.alert.web.controller.ResponseFactory;

@Component
public class EmailCustomEndpoint {
    private static final Logger logger = LoggerFactory.getLogger(EmailCustomEndpoint.class);

    private ResponseFactory responseFactory;
    private ProviderDataAccessor providerDataAccessor;
    private Gson gson;

    @Autowired
    public EmailCustomEndpoint(final CustomEndpointManager customEndpointManager, final ResponseFactory responseFactory, final ProviderDataAccessor providerDataAccessor, final Gson gson) throws AlertException {
        this.responseFactory = responseFactory;
        this.providerDataAccessor = providerDataAccessor;
        this.gson = gson;

        customEndpointManager.registerFunction(EmailDescriptor.KEY_EMAIL_ADDITIONAL_ADDRESSES, this::createEmailOptions);
    }

    public ResponseEntity<String> createEmailOptions(final Map<String, FieldValueModel> fieldValueModels) {
        final FieldValueModel fieldValueModel = fieldValueModels.get(ChannelDistributionUIConfig.KEY_PROVIDER_NAME);
        final String provider = fieldValueModel.getValue().orElse("");

        if (StringUtils.isBlank(provider)) {
            logger.debug("Received provider user email data request with a blank provider");
            return responseFactory.createMessageResponse(HttpStatus.BAD_REQUEST, "You must select a provider to populate data.");
        }

        try {
            final List<ProviderUserModel> pageOfUsers = providerDataAccessor.getAllUsers(provider);
            final LinkedHashSet<LabelValueSelectOption> emailOptions = pageOfUsers.stream()
                                                                           .filter(user -> !user.getOptOut())
                                                                           .map(ProviderUserModel::getEmailAddress)
                                                                           .sorted()
                                                                           .map(LabelValueSelectOption::new)
                                                                           .collect(Collectors.toCollection(LinkedHashSet::new));
            if (emailOptions.isEmpty()) {
                logger.info("No user emails found in the database for the provider: {}", provider);
            }
            final String usersJson = gson.toJson(emailOptions);
            return responseFactory.createOkContentResponse(usersJson);
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            return responseFactory.createMessageResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
