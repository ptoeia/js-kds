import groovy.transform.Field
import groovy.json.JsonSlurper
import com.walrus.Utils
import com.walrus.TemplateHandler
import com.walrus.Deploy
import com.walrus.Notice

// 加载java应用dockerfile
@Field 
def dockerFile = libraryResource 'com/walrus/java/Dockerfile'

@Field 
def MinidockerFile = libraryResource 'com/walrus/java/MiniDockerfile'

// 加载java应用entrypoint.sh
@Field
def entrypointFile = libraryResource 'com/walrus/java/entrypoint.sh'

// 加载java应用jenkins agent yaml
@Field
def agentYaml = libraryResource 'com/walrus/java/jenkinsAgent.yaml'

@Field
// kubeconfig credential id和k8s集群的映射关系
def KubeconfigContextMap = [
    'dev': '2aa41424-xxx-xx-xx-xx',     // dev k8s集群
    'test': '336f75ca-xx-xx-xx-xx',    // test k8s集群
    'pre': 'c91e1d2a-xx-xx-xx-xx',     // pre 集群
    'prod': '0b765a71-xx-xx-xx-xx',    // prod 集群
]

def call(Object WorkflowScript) {
    // following Properties must be exist in Jenkinsfile
    def application = WorkflowScript.application ?: ''
    def port = WorkflowScript.port ?: ''
    def project = WorkflowScript.project ?: ''
    def receivers = WorkflowScript.dingTalkNickNameList ?: ['noperson']
    def jvmOptions = WorkflowScript.jvmOptions ?: '-server -Xms512m -Xmx1g -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512m'
    
    // follwoing Property is optional in in Jenkinsfile
    def dockerBaseImage = WorkflowScript.hasProperty('dockerBaseImage') ? WorkflowScript.dockerBaseImage  : 'openjdk:8-jre'
    // yes or no 
    def isMultModule = WorkflowScript.hasProperty('isMultModule')  ? WorkflowScript.isMultModule  : 'yes'
    
    utils = new Utils()
    render = new TemplateHandler(this)
    deploy = new Deploy()
    notice = new Notice()
    
    deployEnvList = utils.getEnvlistByProject(project)

    pipeline {
      agent {
        // kubernetes plugin
        kubernetes{
          defaultContainer 'jnlp'
          yaml agentYaml
        }
      }
  
      parameters {
        string(name: 'application',defaultValue: application, description: '应用名,一般等于gitlab project name,不能包含特殊字符和下划线',trim: true)
        string(name: 'port',defaultValue: port, description: '应用端口',trim: true)
        string(name: 'project',defaultValue: project, description: '项目代号 etc|puppy|wallaby|lamb|bunny 等等,除中横线和下划线外,不能有特殊字符',trim: true)
        string(name: 'module_name', description: 'java 应用模块名',trim:true)
        string(name: 'jvm_options', defaultValue:jvmOptions,description: 'jvm 堆栈空间参数',trim:true)
        choice(name: 'deploy_env', choices: deployEnvList, description: '部署环境')
        // 该参数仅在发布线上环境时候时使用
        string(
          name: 'replicas_no',
          defaultValue:'1',
          description: 'k8s deployment副本数,如何选择参考下文',
          trim:true
        )
        // 仅仅作为公告在前端展示
        choice(
          name: '建议-Tips',
          choices: ['1.非线上环境默认1个副本', '2.线上环境应用一般为2个副本'],
          description: 'k8s deployment副本数的说明(只是作为Tips展示,无需选择!!)'
        )
      }
  
      environment {
        // commit id for short
        COMMIT_ID = sh(
            returnStdout: true,
            script: "echo \$(git rev-parse --short HEAD)"
        ).trim()
        BUILD_DATE = sh(
            returnStdout: true,
            script: "echo \$(date '+%Y-%m-%d %H:%M')"
        ).trim()
        GIT_COMMIT_MESSAGE = sh(
            returnStdout: true,
            script: "git log --pretty='%B' -1"
        ).trim()
        LANGUAGE = 'java'
        BUILD_USER_ID = "${currentBuild.getBuildCauses()[0].userId}".trim()
        BUILD_USER_NAME = "${currentBuild.getBuildCauses()[0].userName}".trim()
        IMAGE_NAME = "${env.REGISTRY}/${params.project}/${params.module_name}:${env.BUILD_NUMBER}-${env.COMMIT_ID}"
        // Harbor Account
        CREDENTIALS_ID = '2644c74c-xx-xx-xx-xx'
        // kustomize output file
        DEPLOYMENT_FILE = 'deployment.yaml'
        // maven setting.xml 
        MAVEN_SETTINGS_CREDENTIALS_ID = '6b75e50e-xx-xx-xx-xx'
        // kubedog tail timeout
        DEPLOY_TRACK_TIMEOUT = 300
        // gitlab repo for k8s yaml template
        YAML_TEMPLATE_REPO = 'gitlab.walrus.com/devops/zs-kds.git'
        // gitlab account for jenkins
        GITLAB_ACCOUNT = credentials('75504039-xx-xx-xx-xx')
        YAML_TEMPLATE_DIR = "zs-kds/resources/com/walrus/kustomizeTemplate/${params.project}/k8sYamlTemplate"
      }

      stages {

        stage('Clone') {
            steps {
                checkout scm
            }
        }

        stage('编译') {
            steps {
                container('maven') {
                    echo "Start to Build ... "
                    script {
                        // 利用config file Proider plugin 保存 maven settings.xml
                        configFileProvider(
                            [configFile(fileId: "${env.MAVEN_SETTINGS_CREDENTIALS_ID}", targetLocation: 'settings.xml')]) {
                        }
                        // 更改nexus为内网域名地址
                        sh "sed -i 's/8.6.6.6/nexus.walrus.com/g' `grep -rl '8.6.6.6' ./` || echo "
    
                        def buildEnv = params.deploy_env =~ '.*test.*' ? "test" : params.deploy_env
                        sh "mvn -gs settings.xml clean package -Dmaven.test.skip=true -U -P ${buildEnv}"
                 
                        writeFile file: "Dockerfile",text: dockerFile
                        // 替换基础镜像 replace函数无效 用sed替代
                        sh "sed -i 's#BASE_IMAGE#${dockerBaseImage}#g' Dockerfile"
                        writeFile file: "entrypoint.sh",text: entrypointFile
                        
                        if (isMultModule == 'no') {
                            sh "cp target/${params.module_name}.jar ."
                        } else {
                            switch(params.project){
                                case('wallaby'):
                                    sh "cp ./wallaby-cloud-module/${params.module_name}/target/${params.module_name}.jar ."
                                    break
                                case('lamb'):
                                    sh "cp ./lamb-cloud-module/${params.module_name}/target/${params.module_name}.jar ."
                                    break
                                default:
                                    sh "cp ${params.module_name}/target/${params.module_name}.jar ."
                                    break
                            }
                        }
                    }
                }
            }
        }

        stage('推送镜像') {
            steps {
                container('maven') {
                // 推送镜像
                  echo "Start to Push Docker Image... "
                  withCredentials([
                    usernamePassword(
                      credentialsId: CREDENTIALS_ID,
                      passwordVariable: 'dockerHubPassword',
                      usernameVariable: 'dockerHubUser'
                    )
                  ]){
                      sh "docker login -u ${dockerHubUser} -p ${dockerHubPassword} ${REGISTRY}"
                      sh "docker build --build-arg module_name=${params.module_name} -t ${env.IMAGE_NAME} ."
                      sh "docker push ${IMAGE_NAME}"
                  }
                }
            }
        }
        stage('部署测试环境'){
            when {
                expression {params.deploy_env =~ '.*test.*' }
            }
            environment {
              //K8S_NAMESPACE = 'default'
                replicas_no = '1'
                DEPLOY_ENV = 'test'
            }
            steps {
                container('kubetools') {
                    //params.deploy_env = 'test'
                    script {
                        env.K8S_NAMESPACE = utils.getDeployNamespace(params.project,params.deploy_env)
                        kubeconfigId = KubeconfigContextMap[DEPLOY_ENV]
                        switch(params.project) {
                            case('etc'):
                                kubeconfigId = KubeconfigContextMap['test-gz']
                                env.NACOS_ADDRESS = 'nacos.default:8848'
                                env.NACOS_CONFIG_SERVER = 'nacos.default:8848'
                                env.NACOS_CONFIG_NAMESPACE = utils.getDeployNamespace(params.project,params.deploy_env)
                                break
                            case('puppy'):
                                env.NACOS_ADDRESS = 'nacos-dev'
                                env.NACOS_NAMESPACE = 'public'
                                env.NACOS_CONFIG_SERVER = 'nacos.default:8848'
                                env.NACOS_CONFIG_NAMESPACE = utils.getDeployNamespace(params.project,params.deploy_env)
                                break
                            case('wallaby'):
                                env.NACOS_ADDRESS = 'nacos'
                                env.NACOS_NAMESPACE = 'public'
                                env.NACOS_CONFIG_SERVER = 'nacos'
                                break
                            default:
                                env.NACOS_ADDRESS = 'nacos.default:8848'
                                env.NACOS_CONFIG_SERVER = 'nacos.default:8848'
                                env.NACOS_NAMESPACE = utils.getDeployNamespace(params.project,params.deploy_env)
                                break
                        }
                        env.JAVA_OPTS = utils.generateJavaOpts(project,DEPLOY_ENV,params.module_name,params.jvm_options,env.NACOS_CONFIG_SERVER)
                        render.downLoadK8sYamlTemplateOrNot()
                        render.generateYaml(env.LANGUAGE,replicas_no)
                        deploy.k8sdeploy(env.LANGUAGE,kubeconfigId)
                    }
                }
            }
        }
        stage('部署预发环境'){
            when {
                expression {params.deploy_env =~ 'pre.*'}
            }
            environment {
                replicas_no = '1'
                DEPLOY_ENV = 'pre'
            }
            steps {
                container('kubetools') {
                    script {
                        kubeconfigId = KubeconfigContextMap[DEPLOY_ENV]
                        env.K8S_NAMESPACE = utils.getDeployNamespace(params.project,params.deploy_env)
                        env.NACOS_CONFIG_SERVER = 'nacos-truck'
                        env.NACOS_ADDRESS = 'nacos-truck'
                        env.NACOS_CONFIG_NAMESPACE = 'public'
                        env.JAVA_OPTS = utils.generateJavaOpts(project,DEPLOY_ENV,params.module_name,params.jvm_options,env.NACOS_CONFIG_SERVER)
                        render.downLoadK8sYamlTemplateOrNot()
                        render.generateYaml(env.LANGUAGE,replicas_no)
                        deploy.k8sdeploy(env.LANGUAGE,kubeconfigId)
                    }
                }
            }
        }
        stage('部署线上环境'){
            when {
                expression {params.deploy_env == 'prod'}
            }
            environment {
              //replicas_no = 2
              DEPLOY_ENV = 'prod'
            }
            steps {
                container('kubetools') {
                    script {
                        kubeconfigId = KubeconfigContextMap[DEPLOY_ENV]
                        env.K8S_NAMESPACE = utils.getDeployNamespace(params.project,params.deploy_env)
                        switch(params.project) {
                            case "etc":
                                env.NACOS_ADDRESS = 'nacos-truck'
                                env.NACOS_CONFIG_SERVER = 'nacos-truck'
                                break
                            case "puppy":
                                env.NACOS_ADDRESS = 'nacos-puppy'
                                env.NACOS_CONFIG_SERVER = 'nacos-puppy'
                                env.env = 'prod'
                                break
                            case "pony":
                                env.NACOS_ADDRESS = 'nacos-puppy'
                                env.env = 'prod'
                                env.NACOS_CONFIG_SERVER = 'nacos-puppy'
                                break
                            case "bunny":
                                env.NACOS_ADDRESS = 'nacos-bunny'
                                env.NACOS_CONFIG_SERVER = 'nacos-bunny'
                            case "wallaby":
                                env.NACOS_ADDRESS = 'nacos-puppy.puppy'
                                env.NACOS_CONFIG_SERVER = 'nacos-puppy.puppy'

                        }
                        env.NACOS_CONFIG_NAMESPACE = 'public'
                        env.NACOS_NAMESPACE = 'public'
                        env.JAVA_OPTS = utils.generateJavaOpts(project,DEPLOY_ENV,params.module_name,params.jvm_options,env.NACOS_CONFIG_SERVER)
                        timeout(time:10,unit:'MINUTES') {
                            env.confirm = input message: '确认部署?',ok: '点击部署',
                                parameters: [choice(name: 'confirm',choices:['no','yes'],description:"线上部署确认")]
                                if (env.confirm == 'yes'){
                                    render.downLoadK8sYamlTemplateOrNot()
                                    render.generateYaml(env.LANGUAGE,params.replicas_no)
                                    deploy.k8sdeploy(env.LANGUAGE,kubeconfigId) 
                                }
                        }
                    }
                }
            }
        }
    }
    post {
        failure {
            script {
                notice.sendByDingTalk('failure',receivers)
            }
        }
        
        success {
            script {
                notice.sendByDingTalk('success',receivers)
            }
        }
    }  
  }
}