package com.vmware.cnasg.k8s.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.rbac.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import static net.logstash.logback.argument.StructuredArguments.v;

@Service
public class DexNamespaceController extends K8sAbstractController implements Watcher<String> {

    private static final Logger logger = LoggerFactory.getLogger(DexNamespaceController.class);

    @Value("${dex.api-group}")
    private String dexApiGroup;
    @Value("${dex.api-version}")
    private String dexApiVersion;
    @Value("${dex.custom-resource}")
    private String dexCustomResource;
    @Value("${dex.custom-resource-definition}")
    private String dexCustomResourceDefinition;
    @Value("${dex.namespace}")
    private String dexNamespace;
    @Value("${dex.scope}")
    private String dexScope;
    @Value("${dex.user-role}")
    private String dexUserRole;
    @Value("${dex.user-role-file}")
    private String dexUserRoleFile;
    @Value("${dex.user-role-binding-prefix}")
    private String dexUserRoleBindingPrefix;
    @Value("${dex.delete-namespaces-onclose}")
    private Boolean deleteNamespacesOnClose;

    @PostConstruct
    public void init() {
        if (!monitorDexRefreshTokens()) {
            logger.info("DEX resource(refreshtokens) is not available");
            controllers.remove((DexNamespaceController.class.getSimpleName()));
        } else {
            logger.info(DexNamespaceController.class.getCanonicalName() + " started");
        }
    }

    @Override
    public void eventReceived(Action action, String resource) {
        JsonObject jsonObject = new JsonParser().parse(resource).getAsJsonObject();
        String kind = jsonObject.get("kind").getAsString();
        JsonObject claims = jsonObject.getAsJsonObject("claims");
        String email = claims.get("email").getAsString();
        String userID = claims.get("userID").getAsString();
        String userName = claims.get("username").getAsString();
        String idProvider = jsonObject.get("connectorID").getAsString();
        String expectedNamespace = "user-" + email.substring(0,email.indexOf("@"));

        String clientID = jsonObject.get("clientID").getAsString();
        JsonObject metadata = jsonObject.getAsJsonObject("metadata");
        String namespace = metadata.get("namespace").getAsString();
        KubernetesClient client = retrieveK8sClient(clientID,namespace);

        switch (action) {
            case ADDED:
                if (createNamespaceForNewUser(expectedNamespace,client)) {
                    if (bindNewUserAndNamespace(email,expectedNamespace,client)) {
                        logger.info("event received",
                                v("kind",kind),
                                v("status",action.name()),
                                v("email",email),
                                v("userID",userID),
                                v("userName",userName),
                                v("idProvider",idProvider),
                                v("namespace",expectedNamespace));
                    }
                }
                return;
            case DELETED:
                if (deleteNamespace(expectedNamespace,client)) {
                    logger.info("event received",
                            v("kind",kind),
                            v("status",action.name()),
                            v("email",email),
                            v("userID",userID),
                            v("userName",userName),
                            v("idProvider",idProvider),
                            v("namespace",expectedNamespace));
                }
                return;
            case ERROR:
            case MODIFIED:
            default:
                return;
        }
    }

    @Override
    public void onClose(KubernetesClientException cause) {
        logger.info("watcher closed, cause: " + cause);
    }

    @Override
    public boolean stopController() {
        boolean success = false;
        if (deleteNamespacesOnClose) {
            logger.info("deleting all dex user namespaces");
        }
        return true;
    }

    private boolean monitorDexRefreshTokens() {
        boolean success = false;
        CustomResourceDefinition crdDexRefreshTokens =
                getLocalK8sClient().customResourceDefinitions()
                        .withName(dexCustomResourceDefinition).get();
        try {
            if (crdDexRefreshTokens != null) {
                CustomResourceDefinitionContext crdDexContext = new CustomResourceDefinitionContext
                        .Builder()
                        .withGroup(dexApiGroup)
                        .withPlural(dexCustomResource)
                        .withScope(dexScope)
                        .withVersion(dexApiVersion)
                        .build();
                getLocalK8sClient().customResource(crdDexContext).watch(dexNamespace, this);
                success = true;
            } else {
                logger.info("No DEX related resources detected");
            }
        } catch (Exception e) {
            logger.error("error: No Cluster API related resources detected");
        }
        return success;
    }

    private KubernetesClient retrieveK8sClient(String clientID, String namespace) {
        KubernetesClient client = null;
        ConfigMap configMap =
                getLocalK8sClient().configMaps().inNamespace(namespace).withName(clientID).get();
        String data = configMap.getData().get(clientID + ".yaml");
        BufferedReader reader = new BufferedReader(new StringReader((data)));
        try {
            String keyword = "apiServerURL: ";
            String apiServerURL = null;
            String line = reader.readLine();
            while (line != null) {
                if (line.startsWith(keyword)) {
                    apiServerURL = line.substring(line.indexOf(keyword)+keyword.length()) + "/";
//                    logger.info("found apiServerURL[" + apiServerURL + "]");
                    client = provider.getK8sClientByAPIServerURL(apiServerURL);
                    break;
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            logger.error("error", e);
        }
        return client;
    }

    private boolean createDexUserRole(KubernetesClient client) {
        boolean created = false;
        if (client.rbac().clusterRoles().withName(dexUserRole).get() == null) {
            ClusterRole crDex =
                    client.rbac().clusterRoles()
                            .load(DexNamespaceController.class.getResourceAsStream(dexUserRoleFile))
                            .get();
            client.rbac().clusterRoles().create(crDex);
            logger.info("cluster-role[" + dexUserRole + "] created");
            created = true;
        }
        return created;
    }

    private boolean createNamespaceForNewUser(String namespace, KubernetesClient client){
        boolean created = false;
        Namespace ns = client.namespaces().withName(namespace).get();
        if (ns == null) {
            ObjectMeta metadata = new ObjectMeta();
            metadata.setName(namespace);
            Namespace ns1 = new Namespace();
            ns1.setMetadata(metadata);
            Namespace newNamespace = client.namespaces().create(ns1);
            if (newNamespace != null) {
                created = true;
                logger.info("Namespace["+namespace+"] created");
            }
        } else {
            logger.info("Namespace["+namespace+"] already exists");
        }
        return created;
    }

    private boolean deleteNamespace(String namespace, KubernetesClient client) {
        boolean deleted = false;
        Namespace ns = client.namespaces().withName(namespace).get();
        if (ns != null) {
            deleted = client.namespaces().delete(ns);
            if (deleted) {
                logger.info("Namespace["+namespace+"] deleted");
            }
        } else {
            logger.info("Namespace["+namespace+"] is not found");
        }
        return deleted;
    }

    private boolean bindNewUserAndNamespace(String email, String namespace, KubernetesClient client) {
        createDexUserRole(client);
        boolean bound = false;
        String clusterRoleName = dexUserRole;
        String roleBindingName = dexUserRoleBindingPrefix + namespace;
        ClusterRole clusterRole = client.rbac().clusterRoles().withName(clusterRoleName).get();
        if (clusterRole != null) {
            RoleBinding roleBinding = new RoleBindingBuilder()
                    .withNewMetadata()
                    .withName(roleBindingName)
                    .withNamespace(namespace)
                    .endMetadata()
                    .addToSubjects(0, new SubjectBuilder()
                            .withApiGroup("rbac.authorization.k8s.io")
                            .withKind("User")
                            .withName(email)
                            .build()
                    )
                    .withRoleRef(new RoleRefBuilder()
                            .withApiGroup("rbac.authorization.k8s.io")
                            .withKind("ClusterRole")
                            .withName(clusterRoleName)
                            .build()
                    ).build();
            RoleBinding createdRoleBinding = client.rbac().roleBindings()
                    .inNamespace(namespace).create(roleBinding);
            if (createdRoleBinding != null) {
                logger.info("user["+email+"] bound to the namespace["+namespace+"]," +
                        "role-binding[" + roleBindingName + "],cluster-role[" + clusterRoleName + "]");
                bound = true;
            }
        } else {
            logger.info("cluster-role["+ clusterRoleName +"] not found");
        }
        return bound;
    }
}
