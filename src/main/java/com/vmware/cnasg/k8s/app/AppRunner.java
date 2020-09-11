package com.vmware.cnasg.k8s.app;

import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AppRunner implements Watcher<String>  {

    private static final Logger logger = LoggerFactory.getLogger(AppRunner.class);
    private static final String CRD_GROUP = "k8s.cnasg.vmware.com";
    private static final String CRD_PLURAL = "applications";
    private static final String CRD_SCOPE = "Namespaced";
    private static final String CRD_VERSION = "v1alpha1";
    private static final String CRD_FILE = "/cnasg-crd-app.yaml";

    public AppRunner(KubernetesClient client) throws IOException {
        CustomResourceDefinition crdApp = client.customResourceDefinitions()
                .load(AppRunner.class.getResourceAsStream(CRD_FILE)).get();

        CustomResourceDefinitionContext crdAppContext = new CustomResourceDefinitionContext.Builder()
                .withGroup(CRD_GROUP)
                .withPlural(CRD_PLURAL)
                .withScope(CRD_SCOPE)
                .withVersion(CRD_VERSION)
                .build();

        if (client.customResource(crdAppContext) == null) {
            client.customResourceDefinitions().create(crdApp);
        }
        client.customResource(crdAppContext).watch(this);
    }

    @Override
    public void eventReceived(Action action, String resource) {
        switch (action) {
            case ADDED:
            case ERROR:
            case DELETED:
            case MODIFIED:
            default:
                logger.info("action: " + action + ", resource: " + resource);
                return;
        }
    }

    @Override
    public void onClose(KubernetesClientException cause) {
        logger.info("cause: " + cause);
    }
}
