
package com.walrus
import com.cloudbees.groovy.cps.NonCPS

class TemplateHandler implements Serializable{

    def pipeline
    TemplateHandler(pipeline) {
        this.pipeline = pipeline
    }

    //@NonCPS
    def generateYaml(String language,String replicas_no){
        // 使用kustomize 生成相应环境的的k8s yaml文件
        switch(language){
            case "javascript":
                pipeline.steps.sh """
                    kubectl kustomize .k8sYamlTemplate/overlay/${pipeline.env.DEPLOY_ENV} > ${pipeline.env.DEPLOYMENT_FILE} 
                    sed -i 's#walrus-application#${pipeline.params.application}#g;\
                      s#walrus-port#${pipeline.params.port}#g;\
                      s#walrus-namespace#${pipeline.env.K8S_NAMESPACE}#g;\
                      s#walrus-image#${pipeline.env.IMAGE_NAME}#g; \
                      s#walrus-replicas#${replicas_no}#g' ${pipeline.env.DEPLOYMENT_FILE}
                    cat ${pipeline.env.DEPLOYMENT_FILE}
                    """
                break
            case "java":
                pipeline.steps.sh """
                    kubectl kustomize .k8sYamlTemplate/overlay/${pipeline.env.DEPLOY_ENV} > ${pipeline.env.DEPLOYMENT_FILE} 
                    sed -i 's#walrus-application#${pipeline.params.module_name}#g;\
                      s#walrus-port#${pipeline.params.port}#g;\
                      s#walrus-namespace#${pipeline.env.K8S_NAMESPACE}#g;\
                      s#walrus-image#${pipeline.env.IMAGE_NAME}#g; \
                      s#walrus-nacos-address#${pipeline.env.NACOS_CONFIG_SERVER}#g; \
                      s#walrus-nacos-host#${pipeline.env.NACOS_ADDRESS}#g; \
                      s#walrus-nacos-config-namespace#${pipeline.env.NACOS_CONFIG_NAMESPACE}#g; \
                      s#walrus-java-opts#${pipeline.env.JAVA_OPTS}#g; \
                      s#walrus-replicas#${replicas_no}#g' ${pipeline.env.DEPLOYMENT_FILE}
                    cat ${pipeline.env.DEPLOYMENT_FILE}
                    """
                break
            }
       
    }
     // 如果代码根目录中不存在deployment yaml模板 则使用本项目提供的模板
     def downLoadK8sYamlTemplateOrNot() {
        pipeline.steps.sh """
            if [ ! -d .k8sYamlTemplate ]
            then
                mkdir -p .k8sYamlTemplate
                git clone http://jenkins:'${pipeline.env.GITLAB_ACCOUNT_PSW}'@${pipeline.env.YAML_TEMPLATE_REPO}
                cp -rf ${pipeline.env.YAML_TEMPLATE_DIR}/* .k8sYamlTemplate/
            fi
          """
    }
}
