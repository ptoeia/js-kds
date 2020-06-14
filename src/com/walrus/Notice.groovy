package com.walrus
import com.cloudbees.groovy.cps.NonCPS

//@NonCPS 
//def send(String result){
def sendByDingTalk(String result,List receivers){
    switch(result){
        case "failure":
            title = "${params.application} 发布失败! &#x274C;"
            break
        case "success":
            title = "${params.application} 发布成功! &#x2705;"
            break
    }
    // dingtalk插件
    dingtalk (
        // 机器人id jenkins全局配置中获取
        robot: 'xx-xx-xx-xx-xx',
        type: 'MARKDOWN',
        atAll: false,
        title: title,
        text:
            [
              "#### **<font color=green>${title}</font>**",
              "- 发布应用: ${params.application}",
              "- 发布分支: ${env.BRANCH_NAME}",
              "- 发布ID:  ${env.BUILD_ID}",
              "- 变更内容: ${env.GIT_COMMIT_MESSAGE}",
              "- 发布时间: ${env.BUILD_DATE}",
              "- 发布详情: [点击查看详情](${env.BUILD_URL}/console)",
              "- 发布用户: ${env.BUILD_USER_NAME}",
              "- 部署环境: ${params.deploy_env}",
            ],
        at: receivers
    )
}
return this
