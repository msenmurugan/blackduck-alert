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
package com.synopsys.integration.alert.component.settings;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.Timer;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.lang3.StringUtils;
import org.opensaml.saml2.metadata.provider.HTTPMetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.opensaml.xml.parse.ParserPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.saml.metadata.ExtendedMetadata;
import org.springframework.security.saml.metadata.ExtendedMetadataDelegate;
import org.springframework.security.saml.metadata.MetadataManager;
import org.springframework.stereotype.Component;

import com.synopsys.integration.alert.common.descriptor.action.NoTestActionApi;
import com.synopsys.integration.alert.common.rest.model.FieldModel;
import com.synopsys.integration.alert.common.rest.model.FieldValueModel;
import com.synopsys.integration.alert.common.rest.model.UserModel;
import com.synopsys.integration.alert.common.security.EncryptionUtility;
import com.synopsys.integration.alert.database.api.DefaultUserAccessor;
import com.synopsys.integration.alert.workflow.startup.SystemValidator;

@Component
public class SettingsDescriptorActionApi extends NoTestActionApi {
    private static final Logger logger = LoggerFactory.getLogger(SettingsDescriptorActionApi.class);
    private final EncryptionUtility encryptionUtility;
    private final DefaultUserAccessor userAccessor;
    private final SystemValidator systemValidator;
    private final MetadataManager metadataManager;
    private final ParserPool parserPool;
    private final ExtendedMetadata extendedMetadata;

    @Autowired
    public SettingsDescriptorActionApi(final EncryptionUtility encryptionUtility, final DefaultUserAccessor userAccessor, final SystemValidator systemValidator,
        @Lazy final MetadataManager metadataManager, @Lazy final ParserPool parserPool, @Lazy final ExtendedMetadata extendedMetadata) {
        this.encryptionUtility = encryptionUtility;
        this.userAccessor = userAccessor;
        this.systemValidator = systemValidator;
        this.metadataManager = metadataManager;
        this.parserPool = parserPool;
        this.extendedMetadata = extendedMetadata;
    }

    @Override
    public FieldModel readConfig(final FieldModel fieldModel) {
        final Optional<UserModel> defaultUser = userAccessor.getUser(DefaultUserAccessor.DEFAULT_ADMIN_USER);
        final FieldModel newModel = createFieldModelCopy(fieldModel);
        final boolean defaultUserPasswordSet = defaultUser.map(UserModel::getPassword).filter(StringUtils::isNotBlank).isPresent();
        newModel.putField(SettingsDescriptor.KEY_DEFAULT_SYSTEM_ADMIN_PWD, new FieldValueModel(null, defaultUserPasswordSet));
        newModel.putField(SettingsDescriptor.KEY_ENCRYPTION_PWD, new FieldValueModel(null, encryptionUtility.isPasswordSet()));
        newModel.putField(SettingsDescriptor.KEY_ENCRYPTION_GLOBAL_SALT, new FieldValueModel(null, encryptionUtility.isGlobalSaltSet()));
        return newModel;
    }

    @Override
    public FieldModel updateConfig(final FieldModel fieldModel) {
        return saveConfig(fieldModel);
    }

    @Override
    public FieldModel saveConfig(final FieldModel fieldModel) {
        saveDefaultAdminUserPassword(fieldModel);
        saveDefaultAdminUserEmail(fieldModel);
        saveEncryptionProperties(fieldModel);
        addSAMLMetadata(fieldModel);
        systemValidator.validate();
        return createScrubbedModel(fieldModel);
    }

    private FieldModel createScrubbedModel(final FieldModel fieldModel) {
        final HashMap<String, FieldValueModel> fields = new HashMap<>();
        fields.putAll(fieldModel.getKeyToValues());
        fields.remove(SettingsDescriptor.KEY_DEFAULT_SYSTEM_ADMIN_PWD);
        fields.remove(SettingsDescriptor.KEY_ENCRYPTION_PWD);
        fields.remove(SettingsDescriptor.KEY_ENCRYPTION_GLOBAL_SALT);

        return new FieldModel(fieldModel.getDescriptorName(), fieldModel.getContext(), fields);
    }

    private void saveDefaultAdminUserPassword(final FieldModel fieldModel) {
        final String password = fieldModel.getField(SettingsDescriptor.KEY_DEFAULT_SYSTEM_ADMIN_PWD).flatMap(FieldValueModel::getValue).orElse("");
        if (StringUtils.isNotBlank(password)) {
            userAccessor.changeUserPassword(DefaultUserAccessor.DEFAULT_ADMIN_USER, password);
        }
    }

    private void saveDefaultAdminUserEmail(final FieldModel fieldModel) {
        final Optional<FieldValueModel> optionalEmail = fieldModel.getField(SettingsDescriptor.KEY_DEFAULT_SYSTEM_ADMIN_EMAIL);
        if (optionalEmail.isPresent()) {
            userAccessor.changeUserEmailAddress(DefaultUserAccessor.DEFAULT_ADMIN_USER, optionalEmail.flatMap(FieldValueModel::getValue).orElse(""));
        }
    }

    private void saveEncryptionProperties(final FieldModel fieldModel) {
        try {
            final Optional<FieldValueModel> optionalEncryptionPassword = fieldModel.getField(SettingsDescriptor.KEY_ENCRYPTION_PWD);
            final Optional<FieldValueModel> optionalEncryptionSalt = fieldModel.getField(SettingsDescriptor.KEY_ENCRYPTION_GLOBAL_SALT);

            if (optionalEncryptionPassword.isPresent()) {
                final String passwordToSave = optionalEncryptionPassword.get().getValue().orElse("");
                if (StringUtils.isNotBlank(passwordToSave)) {
                    encryptionUtility.updatePasswordField(passwordToSave);
                }
            }

            if (optionalEncryptionSalt.isPresent()) {
                final String saltToSave = optionalEncryptionSalt.get().getValue().orElse("");
                if (StringUtils.isNotBlank(saltToSave)) {
                    encryptionUtility.updateSaltField(saltToSave);
                }
            }
        } catch (final IllegalArgumentException | IOException ex) {
            logger.error("Error saving encryption configuration.", ex);
        }
    }

    private void addSAMLMetadata(final FieldModel fieldModel) {
        final Optional<FieldValueModel> metadataURLFieldValueOptional = fieldModel.getField(SettingsDescriptor.KEY_SAML_METADATA_URL);
        if (metadataURLFieldValueOptional.isPresent()) {
            final FieldValueModel metadataURLFieldValue = metadataURLFieldValueOptional.get();
            final String metadataURL = metadataURLFieldValue.getValue().orElse("");
            if (StringUtils.isNotBlank(metadataURL)) {
                final Timer backgroundTaskTimer = new Timer(true);

                // The URL can not end in a '/' because it messes with the paths for saml
                final String correctedMetadataURL = StringUtils.removeEnd(metadataURL, "/");
                final HTTPMetadataProvider httpMetadataProvider;
                try {
                    httpMetadataProvider = new HTTPMetadataProvider(backgroundTaskTimer, new HttpClient(), correctedMetadataURL);
                    httpMetadataProvider.setParserPool(parserPool);

                    final ExtendedMetadataDelegate idpMetadata = new ExtendedMetadataDelegate(httpMetadataProvider, extendedMetadata);
                    idpMetadata.setMetadataTrustCheck(true);
                    idpMetadata.setMetadataRequireSignature(false);
                    metadataManager.addMetadataProvider(idpMetadata);
                } catch (final MetadataProviderException e) {
                    logger.error("Error setting the SAML metadata.", e);
                }
            }
        }
        try {
            metadataManager.setProviders(Collections.emptyList());
        } catch (final MetadataProviderException e) {
            logger.error("Error clearing the SAML metadata.", e);
        }
    }
}
