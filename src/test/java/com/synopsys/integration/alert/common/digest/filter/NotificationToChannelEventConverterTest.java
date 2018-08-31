package com.synopsys.integration.alert.common.digest.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.transaction.Transactional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.context.web.WebAppConfiguration;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.synopsys.integration.alert.Application;
import com.synopsys.integration.alert.channel.email.EmailGroupChannel;
import com.synopsys.integration.alert.channel.event.ChannelEvent;
import com.synopsys.integration.alert.channel.event.NotificationToChannelEventConverter;
import com.synopsys.integration.alert.channel.hipchat.HipChatChannel;
import com.synopsys.integration.alert.channel.slack.SlackChannel;
import com.synopsys.integration.alert.common.enumeration.DigestType;
import com.synopsys.integration.alert.database.DatabaseDataSource;
import com.synopsys.integration.alert.database.entity.CommonDistributionConfigEntity;
import com.synopsys.integration.alert.database.entity.NotificationContent;
import com.synopsys.integration.alert.database.entity.repository.CommonDistributionRepository;
import com.synopsys.integration.alert.database.entity.repository.NotificationTypeRepository;
import com.synopsys.integration.alert.database.relation.DistributionNotificationTypeRelation;
import com.synopsys.integration.alert.database.relation.repository.DistributionNotificationTypeRepository;
import com.synopsys.integration.alert.workflow.filter.NotificationFilter;
import com.synopsys.integration.blackduck.api.generated.enumeration.NotificationType;
import com.synopsys.integration.test.annotation.DatabaseConnectionTest;
import com.synopsys.integration.test.annotation.ExternalConnectionTest;

@Category({ DatabaseConnectionTest.class, ExternalConnectionTest.class })
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { Application.class, DatabaseDataSource.class })
@TestPropertySource(locations = "classpath:spring-test.properties")
@Transactional
@WebAppConfiguration
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class, TransactionalTestExecutionListener.class, DbUnitTestExecutionListener.class })
public class NotificationToChannelEventConverterTest {

    @Autowired
    private CommonDistributionRepository commonDistributionRepository;

    @Autowired
    private NotificationFilter notificationFilter;

    @Autowired
    private NotificationToChannelEventConverter notificationToEventConverter;

    @Autowired
    private DistributionNotificationTypeRepository distributionNotificationTypeRepository;

    @Autowired
    private NotificationTypeRepository notificationTypeRepository;

    @Before
    public void initializeConfig() {
        cleanUp();

        long configId = 1;
        CommonDistributionConfigEntity slackDistributionConfig = new CommonDistributionConfigEntity(configId++, SlackChannel.COMPONENT_NAME, "Slack Config", "provider_blackduck", DigestType.REAL_TIME, false);
        CommonDistributionConfigEntity hipChatDistributionConfig = new CommonDistributionConfigEntity(configId++, HipChatChannel.COMPONENT_NAME, "HipChat Config", "provider_blackduck", DigestType.REAL_TIME, false);
        CommonDistributionConfigEntity emailDistributionConfig = new CommonDistributionConfigEntity(configId++, EmailGroupChannel.COMPONENT_NAME, "Email Config", "provider_blackduck", DigestType.REAL_TIME, false);

        slackDistributionConfig = commonDistributionRepository.save(slackDistributionConfig);
        hipChatDistributionConfig = commonDistributionRepository.save(hipChatDistributionConfig);
        emailDistributionConfig = commonDistributionRepository.save(emailDistributionConfig);

        for (final NotificationType notificationCategoryEnum : NotificationType.values()) {
            saveDistributionNotificationTypeRelation(slackDistributionConfig.getId(), notificationCategoryEnum.name());
            saveDistributionNotificationTypeRelation(hipChatDistributionConfig.getId(), notificationCategoryEnum.name());
            saveDistributionNotificationTypeRelation(emailDistributionConfig.getId(), notificationCategoryEnum.name());
        }
    }

    @After
    public void cleanUp() {
        commonDistributionRepository.deleteAll();
        distributionNotificationTypeRepository.deleteAll();
        notificationTypeRepository.deleteAll();
    }

    private void saveDistributionNotificationTypeRelation(final Long commonDistributionConfigId, final String notificationType) {
        final DistributionNotificationTypeRelation notificationRelation = new DistributionNotificationTypeRelation(commonDistributionConfigId, notificationType);
        distributionNotificationTypeRepository.save(notificationRelation);
    }

    @Test
    public void createInvalidDigestTypeTest() {
        final NotificationContent notificationModel = createNotificationModel("Project_1", "1.0.0", NotificationType.RULE_VIOLATION);
        final List<NotificationContent> notificationModels = Arrays.asList(notificationModel);

        final Collection<NotificationContent> filteredNotifications = notificationFilter.extractApplicableNotifications(DigestType.DAILY, notificationModels);
        final List<ChannelEvent> channelEvents = notificationToEventConverter.convertToEvents(filteredNotifications);
        assertTrue(channelEvents.isEmpty());
    }

    @Test
    public void createChannelEventTest() {
        final List<CommonDistributionConfigEntity> configEntityList = commonDistributionRepository.findAll();

        final NotificationContent notification_1 = createNotificationModel("Project_1", "1.0.0", NotificationType.RULE_VIOLATION);
        final NotificationContent notification_2 = createNotificationModel("Project_2", "1.0.0", NotificationType.RULE_VIOLATION);
        final NotificationContent notification_3 = createNotificationModel("Project_1", "2.0.0", NotificationType.RULE_VIOLATION);
        final List<NotificationContent> notificationModels = Arrays.asList(notification_1, notification_2, notification_3);

        final Collection<NotificationContent> filteredNotifications = notificationFilter.extractApplicableNotifications(DigestType.REAL_TIME, notificationModels);
        final List<ChannelEvent> channelEvents = notificationToEventConverter.convertToEvents(filteredNotifications);
        assertEquals(configEntityList.size() * filteredNotifications.size(), channelEvents.size());

        channelEvents.forEach(event -> {
            assertNotNull(event.getContent());
        });
    }

    private NotificationContent createNotificationModel(final String projectName, final String projectVersion, final NotificationType notificationType) {
        final Date createdAt = Date.from(ZonedDateTime.now().toInstant());

        final NotificationContent model = new NotificationContent(createdAt, "provider", notificationType.name(), projectName + projectVersion);
        return model;
    }
}