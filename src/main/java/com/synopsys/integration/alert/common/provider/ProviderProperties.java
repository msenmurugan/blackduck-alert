/**
 * blackduck-alert
 *
 * Copyright (C) 2019 Black Duck Software, Inc.
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
package com.synopsys.integration.alert.common.provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.alert.common.configuration.FieldAccessor;
import com.synopsys.integration.alert.common.database.BaseConfigurationAccessor;
import com.synopsys.integration.alert.common.enumeration.ConfigContextEnum;
import com.synopsys.integration.alert.common.exception.AlertDatabaseConstraintException;
import com.synopsys.integration.alert.database.api.configuration.model.ConfigurationModel;

public abstract class ProviderProperties {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected final Provider provider;
    protected final BaseConfigurationAccessor configurationAccessor;

    public ProviderProperties(final Provider provider, final BaseConfigurationAccessor configurationAccessor) {
        this.provider = provider;
        this.configurationAccessor = configurationAccessor;
    }

    // This assumes that there will only ever be one global config for a provider. This may not be the case in the future.
    public Optional<ConfigurationModel> getGlobalConfig() {
        List<ConfigurationModel> configurations = null;
        try {
            configurations = configurationAccessor.getConfigurationByDescriptorNameAndContext(provider.getName(), ConfigContextEnum.GLOBAL);
        } catch (final AlertDatabaseConstraintException e) {
            logger.error("Problem connecting to DB.", e);
        }
        if (null != configurations && !configurations.isEmpty()) {
            return Optional.of(configurations.get(0));
        }
        return Optional.empty();
    }

    protected FieldAccessor createFieldAccessor() {
        return getGlobalConfig()
                   .map(config -> new FieldAccessor(config.getCopyOfKeyToFieldMap()))
                   .orElse(new FieldAccessor(Map.of()));
    }

    protected Optional<String> createOptionalString(final String value) {
        if (StringUtils.isNotBlank(value)) {
            return Optional.of(value);
        }
        return Optional.empty();
    }
}
