package co.cloudify.jenkins.plugin.integrations;

import java.io.File;
import java.io.PrintStream;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import co.cloudify.jenkins.plugin.BlueprintUploadSpec;
import co.cloudify.jenkins.plugin.CloudifyPluginUtilities;
import co.cloudify.jenkins.plugin.Messages;
import co.cloudify.rest.client.CloudifyClient;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.Secret;

/**
 * A build step for applying an Azure ARM template.
 * 
 * @author Isaac Shabtay
 */
public class ARMBuildStep extends IntegrationBuildStep {
    private String subscriptionId;
    private String tenantId;
    private String clientId;
    private Secret clientSecret;
    private String clientSecretAsString;
    private String location;
    private String resourceGroupName;
    private Map<String, Object> parameters;
    private String parametersAsString;
    private String templateFile;

    private transient BlueprintUploadSpec uploadSpec;

    @DataBoundConstructor
    public ARMBuildStep() {
        super();
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    @DataBoundSetter
    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    @DataBoundSetter
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getClientId() {
        return clientId;
    }

    @DataBoundSetter
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Secret getClientSecret() {
        return clientSecret;
    }

    @DataBoundSetter
    public void setClientSecret(Secret clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getClientSecretAsString() {
        return clientSecretAsString;
    }

    @DataBoundSetter
    public void setClientSecretAsString(String clientSecretAsString) {
        this.clientSecretAsString = clientSecretAsString;
    }

    public String getLocation() {
        return location;
    }

    @DataBoundSetter
    public void setLocation(String location) {
        this.location = location;
    }

    public String getResourceGroupName() {
        return resourceGroupName;
    }

    @DataBoundSetter
    public void setResourceGroupName(String resourceGroupName) {
        this.resourceGroupName = resourceGroupName;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    @DataBoundSetter
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public String getParametersAsString() {
        return parametersAsString;
    }

    @DataBoundSetter
    public void setParametersAsString(String parametersAsString) {
        this.parametersAsString = parametersAsString;
    }

    public String getTemplateFile() {
        return templateFile;
    }

    @DataBoundSetter
    public void setTemplateFile(String templateFile) {
        this.templateFile = templateFile;
    }

    @Override
    protected void performImpl(final Run<?, ?> run, final Launcher launcher, final TaskListener listener,
            final FilePath workspace,
            final EnvVars envVars,
            final CloudifyClient cloudifyClient) throws Exception {
        PrintStream logger = listener.getLogger();

        String subscriptionId = CloudifyPluginUtilities.expandString(envVars, this.subscriptionId);
        String tenantId = CloudifyPluginUtilities.expandString(envVars, this.tenantId);
        String clientId = CloudifyPluginUtilities.expandString(envVars, this.clientId);
        String clientSecretAsString = CloudifyPluginUtilities.expandString(envVars, this.clientSecretAsString);
        String location = CloudifyPluginUtilities.expandString(envVars, this.location);
        String resourceGroupName = CloudifyPluginUtilities.expandString(envVars, this.resourceGroupName);
        String parametersAsString = CloudifyPluginUtilities.expandString(envVars, this.parametersAsString);
        String templateFile = CloudifyPluginUtilities.expandString(envVars, this.templateFile);

        Map<String, Object> variablesMap = CloudifyPluginUtilities.getMapFromMapOrString(parametersAsString,
                this.parameters);

        String effectiveClientSecret = CloudifyPluginUtilities.getPassword(this.clientSecret, clientSecretAsString);

        putIfNonNullValue(operationInputs, "azure_subscription_id", subscriptionId);
        putIfNonNullValue(operationInputs, "azure_tenant_id", tenantId);
        putIfNonNullValue(operationInputs, "azure_client_id", clientId);
        putIfNonNullValue(operationInputs, "azure_client_secret", effectiveClientSecret);
        putIfNonNullValue(operationInputs, "location", location);
        putIfNonNullValue(operationInputs, "resource_group_name", resourceGroupName);
        operationInputs.put("parameters", variablesMap);
        operationInputs.put("template_file", templateFile);

        File blueprintPath = prepareBlueprintDirectory("/blueprints/arm/blueprint.yaml");

        try {
            uploadSpec = new BlueprintUploadSpec(blueprintPath);
            super.performImpl(run, launcher, listener, workspace, envVars, cloudifyClient);
        } finally {
            if (!blueprintPath.delete()) {
                logger.println("Failed deleting blueprint file");
            }
            if (!blueprintPath.getParentFile().delete()) {
                logger.println("Failed deleting temporary directory");
            }
        }
    }

    @Override
    protected String getIntegrationName() {
        return "azure-arm";
    }

    @Override
    protected BlueprintUploadSpec getBlueprintUploadSpec() {
        return uploadSpec;
    }

    @Symbol("cfyAzureArm")
    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.ARMBuildStep_DescriptorImpl_displayName();
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("subscriptionId", subscriptionId)
                .append("tenantId", tenantId)
                .append("clientId", clientId)
                // Skip Client Secret
                .append("location", location)
                .append("resourceGroupName", resourceGroupName)
                .append("parameters", parameters)
                .append("parametersAsString", parametersAsString)
                .append("templateFile", templateFile)
                .toString();
    }
}
