package com.blackducksoftware.integration.hub.alert.web.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

import com.blackducksoftware.integration.hub.alert.config.GlobalProperties;
import com.blackducksoftware.integration.hub.alert.exception.AlertException;
import com.blackducksoftware.integration.hub.api.group.GroupRequestService;
import com.blackducksoftware.integration.hub.api.project.ProjectRequestService;
import com.blackducksoftware.integration.hub.model.HubGroup;
import com.blackducksoftware.integration.hub.model.HubProject;
import com.blackducksoftware.integration.hub.model.view.ProjectView;
import com.blackducksoftware.integration.hub.model.view.UserGroupView;
import com.blackducksoftware.integration.hub.model.view.components.MetaView;
import com.blackducksoftware.integration.hub.service.HubServicesFactory;

public class HubDataActionsTest {

    @Test
    public void testGetHubGroupsNullHubServicesFactory() throws Exception {
        final GlobalProperties globalProperties = Mockito.mock(GlobalProperties.class);
        Mockito.when(globalProperties.createHubServicesFactory(Mockito.any(Logger.class))).thenReturn(null);
        final HubDataActions hubDataActions = new HubDataActions(globalProperties);
        try {
            hubDataActions.getHubGroups();
            fail();
        } catch (final AlertException e) {
            assertEquals("Missing global configuration.", e.getMessage());
        }
    }

    @Test
    public void testGetHubGroupsNoGroups() throws Exception {
        final GlobalProperties globalProperties = Mockito.mock(GlobalProperties.class);
        final HubServicesFactory hubServicesFactory = Mockito.mock(HubServicesFactory.class);
        final GroupRequestService groupRequestService = Mockito.mock(GroupRequestService.class);
        Mockito.when(groupRequestService.getAllGroups()).thenReturn(Collections.emptyList());
        Mockito.when(hubServicesFactory.createGroupRequestService()).thenReturn(groupRequestService);
        Mockito.when(globalProperties.createHubServicesFactory(Mockito.any(Logger.class))).thenReturn(hubServicesFactory);
        final HubDataActions hubDataActions = new HubDataActions(globalProperties);
        final List<HubGroup> hubGroups = hubDataActions.getHubGroups();
        assertEquals(0, hubGroups.size());
    }

    @Test
    public void testGetHubGroups() throws Exception {
        final GlobalProperties globalProperties = Mockito.mock(GlobalProperties.class);
        final HubServicesFactory hubServicesFactory = Mockito.mock(HubServicesFactory.class);
        final GroupRequestService groupRequestService = Mockito.mock(GroupRequestService.class);

        final List<UserGroupView> userGroups = new ArrayList<>();
        final Boolean active = true;
        final String username = "User";
        final String href = "href";
        final UserGroupView userGroup = new UserGroupView();
        userGroup.active = active;
        userGroup.name = username;
        final MetaView metaView = new MetaView();
        metaView.href = href;
        userGroup.meta = metaView;
        userGroups.add(userGroup);

        Mockito.when(groupRequestService.getAllGroups()).thenReturn(userGroups);
        Mockito.when(hubServicesFactory.createGroupRequestService()).thenReturn(groupRequestService);
        Mockito.when(globalProperties.createHubServicesFactory(Mockito.any(Logger.class))).thenReturn(hubServicesFactory);
        final HubDataActions hubDataActions = new HubDataActions(globalProperties);
        final List<HubGroup> hubGroups = hubDataActions.getHubGroups();
        assertEquals(1, hubGroups.size());
        final HubGroup hubGroup = hubGroups.get(0);
        assertEquals(active, hubGroup.active);
        assertEquals(username, hubGroup.name);
        assertEquals(href, hubGroup.url);
    }

    @Test
    public void testGetHubProjectsNullHubServicesFactory() throws Exception {
        final GlobalProperties globalProperties = Mockito.mock(GlobalProperties.class);
        Mockito.when(globalProperties.createHubServicesFactory(Mockito.any(Logger.class))).thenReturn(null);
        final HubDataActions hubDataActions = new HubDataActions(globalProperties);
        try {
            hubDataActions.getHubProjects();
            fail();
        } catch (final AlertException e) {
            assertEquals("Missing global configuration.", e.getMessage());
        }
    }

    @Test
    public void testGetHubProjectsNoProjects() throws Exception {
        final GlobalProperties globalProperties = Mockito.mock(GlobalProperties.class);
        final HubServicesFactory hubServicesFactory = Mockito.mock(HubServicesFactory.class);
        final ProjectRequestService projectRequestService = Mockito.mock(ProjectRequestService.class);
        Mockito.when(projectRequestService.getAllProjects()).thenReturn(Collections.emptyList());
        Mockito.when(hubServicesFactory.createProjectRequestService()).thenReturn(projectRequestService);
        Mockito.when(globalProperties.createHubServicesFactory(Mockito.any(Logger.class))).thenReturn(hubServicesFactory);
        final HubDataActions hubDataActions = new HubDataActions(globalProperties);
        final List<HubProject> hubProjects = hubDataActions.getHubProjects();
        assertEquals(0, hubProjects.size());
    }

    @Test
    public void testGetHubProjects() throws Exception {
        final GlobalProperties globalProperties = Mockito.mock(GlobalProperties.class);
        final HubServicesFactory hubServicesFactory = Mockito.mock(HubServicesFactory.class);
        final ProjectRequestService projectRequestService = Mockito.mock(ProjectRequestService.class);

        final List<ProjectView> projectViews = new ArrayList<>();
        final String projectName = "projectName";
        final String href = "href";
        final ProjectView projectView = new ProjectView();
        projectView.name = projectName;
        final MetaView metaView = new MetaView();
        metaView.href = href;
        projectView.meta = metaView;
        projectViews.add(projectView);

        Mockito.when(projectRequestService.getAllProjects()).thenReturn(projectViews);
        Mockito.when(hubServicesFactory.createProjectRequestService()).thenReturn(projectRequestService);

        Mockito.when(globalProperties.createHubServicesFactory(Mockito.any(Logger.class))).thenReturn(hubServicesFactory);
        final HubDataActions hubDataActions = new HubDataActions(globalProperties);
        final List<HubProject> hubProjects = hubDataActions.getHubProjects();
        assertEquals(1, hubProjects.size());
        final HubProject hubProject = hubProjects.get(0);
        assertEquals(projectName, hubProject.name);
        assertEquals(href, hubProject.url);
    }
}
