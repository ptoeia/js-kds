package com.walrus
import com.cloudbees.groovy.cps.NonCPS

class Utils implements Serializable{

    //def pipeline
    //Utils(pipeline) {
    //    this.pipeline = pipeline
    //}
    
    // 各项部署环境和k8s namspace的映射关系
    def k8snamespaceEnvMap = [
        'etc':[
            'test-01':'walrus-etc-service-dev-01',
            'test-02':'walrus-etc-service-dev-02',
            'test-03':'walrus-etc-service-dev-03',
            'test-04':'walrus-etc-service-dev-04',
            'test-05':'walrus-etc-service-dev-05',
            'test-06':'walrus-etc-service-dev-06',
            'test-07':'walrus-etc-service-dev-07',
            'test-08':'walrus-etc-service-dev-08',
            'test-09':'walrus-etc-service-dev-09',
            'test-10':'walrus-etc-service-dev-10',
            'test-11':'walrus-etc-service-dev-11',
            'walrus-etc-service-pressure-test': 'walrus-etc-service-pressure-test',
            'walrus-etc-services-test-hh': 'walrus-etc-services-test-hh',
            'walrus-etc-service-test-hes': 'walrus-etc-service-test-hes',
            'walrus-etc-service-test-ccb': 'walrus-etc-service-test-ccb',
            'pre':'zsetc',
            'prod': 'zsetc'
        ],
        'lamb':[
            'test-01':'lamb',
            'test-02':'lamb-02',
            'prod':'lamb',
            'pre':'lamb',
        ],
        'puppy':[
            'puppy-lingfei-test':'puppy-lingfei',
            'puppy-test':'puppy-test',
            'puppy-test-02':'puppy-test-02',
            'puppy-test-03':'puppy-test-03',
            'puppy-test-04':'puppy-test-04',
            'jianhang-puppy-test':'jianhang-puppy',
            'pre':'puppy',
            'prod':'puppy',
        ],
        'bunny':[
            'test-01':'bunny-test',
            'pre':'bunny',
            'prod':'bunny',
        ],
        'wallaby':[
            'test-01':'wallaby',
            'pre':'wallaby',
            'prod':'wallaby'
        ]
    ]
    /**
     * 获取项目部署环境列表
     * @param project
     * @return
     */
    def getEnvlistByProject(String project) {
        def deployEnvList = [:]
        def projectEnvMap = this.k8snamespaceEnvMap[project]
        if (projectEnvMap.size() > 0 ){
            deployEnvList = new ArrayList<String>(projectEnvMap.keySet())
        }
        return deployEnvList
    }
    /**
     * 根据项目和部署环境获取k8s namespace
     * @param project
     * @param deployEnv
     * @return
     */
    def getDeployNamespace(String project,String deployEnv) {
        return this.k8snamespaceEnvMap[project][deployEnv]
    }
     
     /**
     * 根据项目和部署环境获取k8s namespace
     * @param project
     * @param deployEnv 部署环境
     * @param module java应用模块名
     * @param jvmOpts jvm堆栈空间参数 例如: -server -Xms512m -Xmx512m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512ms
     * @return
     */
    def generateJavaOpts(String project,String deployEnv,String module,String jvmOpts,String naocsConfigServer) {
        
        def logDir="/log/${module}"
        def projectName="${module}"
        def skywalkingAgent="/usr/skywalking/agent/skywalking-agent.jar"
        def skywalkingBackend="oap.skywalking:11800"
        def skywalkingServiceName="${module}"

        //JVM GC options
        def gcOpts=("-verbose:gc"
                       +" -XX:+PrintGCDetails"
                       +" -XX:+PrintGCDateStamps"
                       +" -XX:+PrintGCTimeStamps"
                       +" -XX:+UseGCLogFileRotation"
                       +" -XX:NumberOfGCLogFiles=100"
                       +" -XX:GCLogFileSize=100K"
                       +" -Xloggc:${logDir}/gc.log"
                       +" -DLOG_PATH=${logDir}/log.log")
        // HeapDump options
        def dumpOpts="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${logDir}"
        // sentinel options
        def sentinelOpts="-Dcsp.sentinel.api.port=8719 -Dcsp.sentinel.dashboard.server=sentinel:8280"
        // nacos options
        def nacosOpts="-DNACOS_CONFIG_SERVER=${naocsConfigServer}"
        // skywalking options
        def skywalkingOpts=("-Dskywalking.agent.service_name=${skywalkingServiceName}"
                          + " -Dskywalking.collector.backend_service=${skywalkingBackend}"
                          + " -javaagent:${skywalkingAgent}")
        def javaOpts = ''
        switch(project){
            case "etc":
               javaOpts = "${jvmOpts} ${gcOpts} ${dumpOpts} ${sentinelOpts} ${nacosOpts} ${skywalkingOpts} -Dproject.name=${projectName}"
               break
            case "puppy":
               javaOpts = "${jvmOpts} ${gcOpts} ${dumpOpts} ${sentinelOpts} ${nacosOpts} ${skywalkingOpts} -Dproject.name=${projectName}"
               break
            case "bunny":
               javaOpts = "${jvmOpts} ${gcOpts} ${dumpOpts} ${sentinelOpts} ${nacosOpts} -Dproject.name=${projectName}"
               break
        }
        return javaOpts
    }
    
}
