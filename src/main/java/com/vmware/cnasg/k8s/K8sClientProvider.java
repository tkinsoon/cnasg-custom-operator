package com.vmware.cnasg.k8s;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class K8sClientProvider extends HashMap<String, KubernetesClient> implements Watcher<String>  {

    private static final Logger logger = LoggerFactory.getLogger(K8sClientProvider.class);
    private static final String LOCAL_K8S_CLIENT_NAME = "local";

    @Value("${capi.api-group}")
    private String capiApiGroup;
    @Value("${capi.api-version}")
    private String capiApiVersion;
    @Value("${capi.custom-resource}")
    private String capiCustomResource;
    @Value("${capi.custom-resource-definition}")
    private String capiCustomResourceDefinition;
    @Value("${capi.namespace}")
    private String capiNamespace;
    @Value("${capi.scope}")
    private String capiScope;
    @Value("${capi.user-role}")

    @PostConstruct
    public void init() {
        KubernetesClient client = new DefaultKubernetesClient();
        put(LOCAL_K8S_CLIENT_NAME,client);
        if (!loadK8sWorkloadClusterClients()) {
            logger.info("No Cluster API installed");
        }
    }

    private boolean loadK8sWorkloadClusterClients() {
        boolean success = false;
        try {
            CustomResourceDefinition crdCluster =
                    getLocalK8sClient().customResourceDefinitions()
                            .withName(capiCustomResourceDefinition).get();
            if (crdCluster != null) {
                CustomResourceDefinitionContext crdClusterContext = new CustomResourceDefinitionContext
                        .Builder()
                        .withGroup(capiApiGroup)
                        .withPlural(capiCustomResource)
                        .withScope(capiScope)
                        .withVersion(capiApiVersion)
                        .build();
                getLocalK8sClient().customResource(crdClusterContext).watch(capiNamespace, this);
                success = true;
            } else {
                logger.info("No Cluster API related resources detected");
            }
        } catch (Exception e) {
            logger.error("error: No Cluster API related resources detected");
        }
        return success;
    }

    public KubernetesClient getLocalK8sClient() {
        return get(LOCAL_K8S_CLIENT_NAME);
    }

    public KubernetesClient getK8sClientByClusterName(String clusterName) {
        return get(clusterName);
    }

    public KubernetesClient getK8sClientByAPIServerURL(String apiServerURL) {
        KubernetesClient client = null;
        for (Map.Entry<String, KubernetesClient> entry : entrySet()) {
            if (entry.getValue().getMasterUrl().toString().equalsIgnoreCase(apiServerURL)) {
                client = entry.getValue();
                break;
            }
        }
        return client;
    }

    public KubernetesClient removeK8sClientByClusterName(String clusterName) {
        return remove(clusterName);
    }

    @Override
    public void eventReceived(Action action, String resource) {
        switch (action) {
            case ADDED:
                JsonObject jsonObject = new JsonParser().parse(resource).getAsJsonObject();
                String kind = jsonObject.get("kind").getAsString();
                JsonObject metadata = jsonObject.getAsJsonObject("metadata");
                String wClusterName = metadata.get("name").getAsString();
                String wClusterKubeConfigSecret = wClusterName + "-kubeconfig";
                logger.info("found workload cluster[" + wClusterName + "] from namespace[" + capiNamespace + "], " +
                        "retrieve kubeconfig from secret[" + wClusterKubeConfigSecret + "]");
                Secret secret = getLocalK8sClient().secrets().inNamespace(capiNamespace)
                        .withName(wClusterKubeConfigSecret).get();
                String data = secret.getData().get("value");
                String kubeConfig = new String(Base64.getDecoder().decode(data));
                KubernetesClient workloadClusterClient = null;
                try {
                    workloadClusterClient = new DefaultKubernetesClient(Config.fromKubeconfig(kubeConfig));
                    put(wClusterName,workloadClusterClient);
                    logger.info("client of workload cluster[" + wClusterName + "] added to the k8s client provider");
                } catch (IOException e) {
                    logger.error("error",e);
                }
                return;

            case DELETED:
                jsonObject = new JsonParser().parse(resource).getAsJsonObject();
                metadata = jsonObject.getAsJsonObject("metadata");
                wClusterName = metadata.get("name").getAsString();
                if (removeK8sClientByClusterName(wClusterName) != null) {
                    logger.info("removed workload cluster[" + wClusterName + "] successfully");
                }
                return;

            case ERROR:
            case MODIFIED:
            default:
                logger.info("action: " + action);
                return;
        }
    }

    @Override
    public void onClose(KubernetesClientException cause) {
        logger.info("watcher closed, cause: " + cause);
    }

}
