package com.vmware.cnasg.k8s.watcher;

import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClient;

public class ReplicaSetWatcher extends AbstractWatcher<ReplicaSet> {

    public ReplicaSetWatcher(KubernetesClient client) {
        super(client);
    }
}
