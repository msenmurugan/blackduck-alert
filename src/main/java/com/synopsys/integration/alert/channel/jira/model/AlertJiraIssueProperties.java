package com.synopsys.integration.alert.channel.jira.model;

import java.util.Optional;

public class AlertJiraIssueProperties {
    private String category;
    private String bomComponentUri;
    private String policyName;

    public AlertJiraIssueProperties() {
        // For serialization
    }

    public AlertJiraIssueProperties(final String category, final String bomComponentUri, final String policyName) {
        this.category = category;
        this.bomComponentUri = bomComponentUri;
        this.policyName = policyName;
    }

    public String getCategory() {
        return category;
    }

    public String getBomComponentUri() {
        return bomComponentUri;
    }

    public Optional<String> getPolicyName() {
        return Optional.ofNullable(policyName);
    }

}
