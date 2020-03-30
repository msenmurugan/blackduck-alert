package com.synopsys.integration.alert.web.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synopsys.integration.alert.common.ContentConverter;
import com.synopsys.integration.alert.common.rest.ResponseFactory;
import com.synopsys.integration.alert.common.security.authorization.AuthorizationManager;
import com.synopsys.integration.alert.component.tasks.TaskManagementDescriptorKey;
import com.synopsys.integration.alert.web.config.ConfigController;
import com.synopsys.integration.alert.web.controller.BaseController;

@RestController
@RequestMapping(TaskController.TASK_BASE_PATH)
public class TaskController extends BaseController {
    public static final String TASK_BASE_PATH = ConfigController.CONFIGURATION_PATH + "/task";
    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);
    private final ContentConverter contentConverter;
    private final ResponseFactory responseFactory;
    private final AuthorizationManager authorizationManager;
    private final TaskActions taskActions;
    private final TaskManagementDescriptorKey descriptorKey;

    @Autowired
    public TaskController(ContentConverter contentConverter, ResponseFactory responseFactory, AuthorizationManager authorizationManager, TaskActions taskActions,
        TaskManagementDescriptorKey descriptorKey) {
        this.contentConverter = contentConverter;
        this.responseFactory = responseFactory;
        this.authorizationManager = authorizationManager;
        this.taskActions = taskActions;
        this.descriptorKey = descriptorKey;
    }

    @GetMapping
    public ResponseEntity<String> getAllTasks() {
        if (!hasGlobalPermission(authorizationManager::hasReadPermission, descriptorKey)) {
            return responseFactory.createForbiddenResponse();
        }
        return responseFactory.createOkContentResponse(contentConverter.getJsonString(taskActions.getTasks()));
    }

}