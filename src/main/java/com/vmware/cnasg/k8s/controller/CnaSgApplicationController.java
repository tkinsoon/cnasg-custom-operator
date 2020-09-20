package com.vmware.cnasg.k8s.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

import static net.logstash.logback.argument.StructuredArguments.v;

@Service
public class CnaSgApplicationController extends K8sAbstractController implements Watcher<String>  {

    private static final Logger logger = LoggerFactory.getLogger(CnaSgApplicationController.class);

    @Value("${cna.api-group}")
    private String cnaApiGroup;
    @Value("${cna.api-version}")
    private String cnaApiVersion;
    @Value("${cna.custom-resource}")
    private String cnaCustomResource;
    @Value("${cna.custom-resource-definition}")
    private String cnaCustomResourceDefinition;
    @Value("${cna.custom-resource-definition-file}")
    private String cnaCustomResourceDefinitionFile;
    @Value("${cna.scope}")
    private String cnaScope;

    @PostConstruct
    public void init() {
        if (createCnaSgApplicationCRD()) {
            logger.info(CnaSgApplicationController.class.getCanonicalName() + " started");
        }
    }

    private boolean createCnaSgApplicationCRD() {
        boolean success = false;
        try {
            if (getLocalK8sClient().customResourceDefinitions()
                    .withName(cnaCustomResourceDefinition).get() == null) {
                CustomResourceDefinition crdApp = getLocalK8sClient().customResourceDefinitions()
                        .load(CnaSgApplicationController.class
                                .getResourceAsStream(cnaCustomResourceDefinitionFile)).get();
                getLocalK8sClient().customResourceDefinitions().create(crdApp);
                logger.info("new CRD[" + cnaCustomResourceDefinition + "] created");
            }
            CustomResourceDefinitionContext crdAppContext = new CustomResourceDefinitionContext
                    .Builder()
                    .withGroup(cnaApiGroup)
                    .withPlural(cnaCustomResource)
                    .withScope(cnaScope)
                    .withVersion(cnaApiVersion)
                    .build();
            getLocalK8sClient().customResource(crdAppContext).watch(this);
            success = true;
        } catch (Exception e) {
            logger.error("error", e);
        }
        return success;
    }

    @Override
    public void eventReceived(Action action, String resource) {
        JsonObject jsonObject = new JsonParser().parse(resource).getAsJsonObject();
        String kind = jsonObject.get("kind").getAsString();
        JsonObject metadata = jsonObject.getAsJsonObject("metadata");
        String name = metadata.get("name").getAsString();
        String namespace = metadata.get("namespace").getAsString();
        JsonObject spec = jsonObject.getAsJsonObject("spec");
        String location = spec.get("location").getAsString();

        logger.info("event received",
                v("kind",kind),
                v("status",action.name()),
                v("name",name),
                v("namespace",namespace),
                v("location",location));

        switch (action) {
            case ADDED:
            case ERROR:
            case DELETED:
            case MODIFIED:
            default:
                return;
        }
    }

    @Override
    public void onClose(KubernetesClientException cause) {
        logger.info("watcher closed, cause: " + cause);
    }

}
