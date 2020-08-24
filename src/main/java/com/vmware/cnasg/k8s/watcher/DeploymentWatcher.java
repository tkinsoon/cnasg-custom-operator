package com.vmware.cnasg.k8s.watcher;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;

public class DeploymentWatcher extends AbstractWatcher<Deployment> {

    public DeploymentWatcher(KubernetesClient client) {
        super(client);
    }
}
