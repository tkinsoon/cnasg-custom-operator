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

public class DexCustomResourceWatcher implements Watcher<String> {

    private static final Logger logger = LoggerFactory.getLogger(DexCustomResourceWatcher.class);
    private KubernetesClient client;
    private String watcherName = "refreshtokens.dex.coreos.com";
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
        JsonObject claims = jsonObject.getAsJsonObject("claims");
        String email = claims.get("email").getAsString();
        String userID = claims.get("userID").getAsString();
        String userName = claims.get("username").getAsString();
        String idProvider = jsonObject.get("connectorID").getAsString();
        String expectedNamespace = "user-" + email.substring(0,email.indexOf("@"));
        String log = "eventReceived," +
                "action:" + action.name() + "," +
                "email:" + email + "," +
                "userID:" + userID + "," +
                "userName:'" + userName + "'," +
                "idProvider:" + idProvider + "," +
                "expectedNamespace:" + expectedNamespace;

        switch (action) {
            case ADDED:
                logger.info(log);
                if (createNamespaceForNewUser(expectedNamespace)) {
                    bindNewUserAndNamespace(email,expectedNamespace);
                }
                return;
            case ERROR:
            case DELETED:
            case MODIFIED:
            default:
                logger.info(log);
                return;
        }
    }

    @Override
    public void onClose(KubernetesClientException cause) {
        logger.info("Watcher[" + watcherName + "] close due to " + cause);
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
                logger.info("Namespace[" + namespace + "] created");
            }
        } else {
            logger.info("Namespace[" + namespace + "] already exists");
        }
        return created;
    }

    private boolean bindNewUserAndNamespace(String email, String namespace) {
        boolean bound = false;
        String clusterRoleName = dexUserRole;
        String roleBindingName = dexUserRoleBindingPrefix + namespace;
        logger.info("clusterRoleName" + clusterRoleName + " roleBindingName: " + roleBindingName);
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
                String log = "User[email:" + email + ",namespace:" + namespace + ",role-binding:" + roleBindingName + "] " +
                        "bound with cluster-role[" + clusterRoleName + "]";
                logger.info(log);
                bound = true;
            }
        } else {
            logger.info("cluster-role[" + clusterRoleName + "] not found");
        }
        return bound;
    }
}
