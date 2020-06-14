import groovy.transform.Field
import groovy.json.JsonSlurper
import com.walrus.Utils
import com.walrus.TemplateHandler
import com.walrus.Deploy
import com.walrus.Notice


// 加载前端项目dockerfile
@Field
def dockerFile = libraryResource 'com/walrus/front/Dockerfile'

// 加载前端jenkins agent yaml
@Field
def agentYaml = libraryResource 'com/walrus/front/jenkinsAgent.yaml'

@Field
// kubeconfig credential id和k8s集群的映射关系
def KubeconfigContextMap = [
    'dev': '2aa41424-xxx-xx-xx-xx',     // dev k8s集群
    'test': '336f75ca-xx-xx-xx-xx',    // test k8s集群
    'pre': 'c91e1d2a-xx-xx-xx-xx',     // pre 集群
    'prod': '0b765a71-xx-xx-xx-xx',    // prod 集群
]

def call(Object WorkflowScript) {
    def application = WorkflowScript.application ?: ''
    def port = WorkflowScript.port ?: ''
    def project = WorkflowScript.project ?: 'etc'
    def receivers = WorkflowScript.dingTalkNickNameList ?: ['noperson']
    
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
        string(name: 'project',defaultValue: project, description: '项目代号 puppy|wallaby|lamb|etc 等等,除中横线和下划线外,不能有特殊字符',trim: true)
        choice(name: 'deploy_env', choices: deployEnvList, description: '部署环境')
        //string(
        //  name: 'replicas_no',
        //  defaultValue: '1',
        //  description: 'k8s deployment副本数,详情见下文',
        //  trim: true
        //)
        //// 仅仅作为公告在前端展示
        //choice(
        //  name: 'Tips',
        //  choices: ['1.非线上环境默认1个实例', '2.etc业务线上3个副本', '3.江苏etc线上1个副本','4.线上环境其他应用一般为2个副本'],
        //  description: 'k8s deployment副本数的说明(只是作为公告展示,无需选择哈)'
        //)
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
        LANGUAGE = 'javascript'
        BUILD_USER_ID = "${currentBuild.getBuildCauses()[0].userId}".trim()
        BUILD_USER_NAME = "${currentBuild.getBuildCauses()[0].userName}".trim()
        IMAGE_NAME = "${env.REGISTRY}/${params.project}/${params.application}:${env.BUILD_NUMBER}-${env.COMMIT_ID}"
        // Harbor Account
        CREDENTIALS_ID = '2644c74c-xx-xx-xx-xx'
        // kustomize output file
        DEPLOYMENT_FILE = 'deployment.yaml'
        // nexus
        NPM_REGISTRY = 'nexus.walrus.com'
        // kubedog tail timeout
        DEPLOY_TRACK_TIMEOUT = 300
        // gitlab repo for k8s yaml template
        YAML_TEMPLATE_REPO = 'gitlab.walrus.com/devops/zs-kds.git'
        // gitlab account for jenkins
        GITLAB_ACCOUNT = credentials('75504039-xx-xx-xx-b357e13f708b')
        YAML_TEMPLATE_DIR = 'zs-kds/resources/com/walrus/kustomizeTemplate/fe/k8sYamlTemplate'
      }
      stages {
        stage('Clone') {
            steps {
                checkout scm
            }
        }

        stage('编译') {
            steps {
                container('node') {
                    echo "Start to Build ... "
                    writeFile file: "Dockerfile",text: dockerFile
                    script {
                        def build_env = params.deploy_env =~ '.*test.*' ? "test" : params.deploy_env
                        sh """
                             yarn install --registry=${env.NPM_REGISTRY}
                             yarn run build:${build_env}
                             docker build -t ${env.IMAGE_NAME} .
                           """
                    }
                }
            }
        }

        stage('推送镜像') {
            steps {
                container('node') {
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
                      sh "docker push ${IMAGE_NAME}"
                  }
                }
            }
        }
        stage('部署测试环境'){
            when {
                expression {params.deploy_env =~ 'test.*' }
            }
            environment {
                //K8S_NAMESPACE = 'default'
                replicas_no = '1'
                DEPLOY_ENV = 'test'
            }
            steps {
                container('kubetools') {
                    script {
                        kubeconfigId = KubeconfigContextMap[DEPLOY_ENV]
                        render.downLoadK8sYamlTemplateOrNot()
                        env.K8S_NAMESPACE = utils.getDeployNamespace(params.project,params.deploy_env)
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
                        render.downLoadK8sYamlTemplateOrNot()
                        env.K8S_NAMESPACE = utils.getDeployNamespace(params.project,params.deploy_env)
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
                        replicas_no = '2'
                        echo "${env.K8S_NAMESPACE}"
                        timeout(time:10,unit:'MINUTES') {
                            env.confirm = input message: '确认部署?',ok: '点击部署',
                                parameters: [choice(name: 'confirm',choices:['否','是'],description:"线上部署确认")]
                                if (env.confirm == '是'){
                                    render.downLoadK8sYamlTemplateOrNot()
                                    render.generateYaml(env.LANGUAGE,replicas_no)
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
