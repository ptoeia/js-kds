package com.walrus

    //@NonCPS
def k8sdeploy(String language,String kubeconfigId){
    //generateYaml(replicas_no)
    //try {
    //pipeline.steps.echo "本次部署集群id: ${kubeconfigId}"
    //}catch(err){
    //  throw err
    //}
    deploymentName = "${params.application}"
    if (language == 'java'){
       deploymentName = "${params.module_name}"
    }
    withCredentials([
      kubeconfigContent(
        credentialsId: "${kubeconfigId}",
        variable: 'KUBECONFIG_CONTENT'
    )]) {
        sh "echo '本次部署集群 credential id: ${kubeconfigId}' "
        sh "set +x; echo -e '${KUBECONFIG_CONTENT}' > kubeconfig"
        sh "kubectl --kubeconfig kubeconfig create namespace ${env.K8S_NAMESPACE} || echo"
        //import harobr secret from zs-devops namespcace
        sh "kubectl --kubeconfig kubeconfig get secret registry-pull-secret --namespace=zs-devops --export -o yaml | kubectl --kubeconfig kubeconfig apply --namespace=${env.K8S_NAMESPACE} -f -"
        sh "kubectl --kubeconfig  kubeconfig apply -f ${env.DEPLOYMENT_FILE}"
        sh """
            kubedog --kube-config kubeconfig rollout track \
            -n "${env.K8S_NAMESPACE}" \
            deployment "${deploymentName}" \
            -t "${env.DEPLOY_TRACK_TIMEOUT}"
            """
    }
}

return this
