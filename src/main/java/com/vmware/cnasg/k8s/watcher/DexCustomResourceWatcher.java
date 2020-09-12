package com.vmware.cnasg.k8s.watcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.rbac.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.logstash.logback.argument.StructuredArguments.v;

public class DexCustomResourceWatcher implements Watcher<String> {

    private static final Logger logger = LoggerFactory.getLogger(DexCustomResourceWatcher.class);
    private KubernetesClient client;
    private String dexUserRole;
    private String dexUserRoleBindingPrefix;

    public DexCustomResourceWatcher(KubernetesClient client,
                                    String dexUserRole, String dexUserRoleBindingPrefix) {
        this.dexUserRole = dexUserRole;
        this.dexUserRoleBindingPrefix = dexUserRoleBindingPrefix;
        this.client = client;
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

        logger.info("event received",
                v("kind",kind),
                v("status",action.name()),
                v("email",email),
                v("userID",userID),
                v("userName",userName),
                v("idProvider",idProvider),
                v("expectedNamespace",expectedNamespace));

        switch (action) {
            case ADDED:
                if (createNamespaceForNewUser(expectedNamespace)) {
                    bindNewUserAndNamespace(email,expectedNamespace);
                }
                return;
            case ERROR:
            case DELETED:
            case MODIFIED:
            default:
                return;
        }
    }

    @Override
    public void onClose(KubernetesClientException cause) {
        logger.info("watcher closed",
                v("cause",cause));
    }

    private boolean createNamespaceForNewUser(String namespace){
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
                logger.info("Namespace created",
                        v("namespace",namespace));
            }
        } else {
            logger.info("Namespace already exists",
                    v("namespace", namespace));
        }
        return created;
    }

    private boolean bindNewUserAndNamespace(String email, String namespace) {
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
                logger.info("user bound to the namespace",
                        v("status","BOUND"),
                        v("email",email),
                        v("namespace",namespace),
                        v("roleBinding",roleBindingName),
                        v("clusterRole",clusterRoleName));
                bound = true;
            }
        } else {
            logger.info("cluster-role not found",
                    v("status","NOT_FOUND"),
                    v("clusterRole",clusterRoleName));
        }
        return bound;
    }
}
