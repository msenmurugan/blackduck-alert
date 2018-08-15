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
package com.synopsys.integration.alert.web.scheduling;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synopsys.integration.alert.database.scheduling.GlobalSchedulingConfigEntity;
import com.synopsys.integration.alert.web.controller.GlobalConfigController;
import com.synopsys.integration.alert.common.ContentConverter;

@RestController
@RequestMapping(GlobalConfigController.GLOBAL_PATH + "/scheduling")
public class GlobalSchedulingConfigController extends GlobalConfigController<GlobalSchedulingConfig> {
    private final GlobalSchedulingHandler globalSchedulingHandler;

    @Autowired
    public GlobalSchedulingConfigController(final GlobalSchedulingConfigActions configActions, final ContentConverter contentConverter) {
        globalSchedulingHandler = new GlobalSchedulingHandler(GlobalSchedulingConfigEntity.class, GlobalSchedulingConfig.class, configActions, contentConverter);
    }

    @Override
    public List<GlobalSchedulingConfig> getConfig(@RequestParam(value = "id", required = false) final Long id) {
        return globalSchedulingHandler.getConfig(id);
    }

    @Override
    public ResponseEntity<String> postConfig(@RequestBody(required = false) final GlobalSchedulingConfig restModel) {
        return globalSchedulingHandler.postConfig(restModel);
    }

    @Override
    public ResponseEntity<String> putConfig(@RequestBody(required = false) final GlobalSchedulingConfig restModel) {
        return globalSchedulingHandler.putConfig(restModel);
    }

    @Override
    public ResponseEntity<String> validateConfig(@RequestBody(required = false) final GlobalSchedulingConfig restModel) {
        return globalSchedulingHandler.validateConfig(restModel);
    }

    @Override
    public ResponseEntity<String> deleteConfig(@RequestBody(required = false) final GlobalSchedulingConfig restModel) {
        return globalSchedulingHandler.deleteConfig(restModel);
    }

    @Override
    public ResponseEntity<String> testConfig(@RequestBody(required = false) final GlobalSchedulingConfig restModel) {
        return globalSchedulingHandler.doNotAllowHttpMethod();
    }

}