import groovy.json.JsonSlurperClassic
import groovy.transform.Field

//map 所有服务器信息
@Field final def serversMap = [:]

def init() {
    // 获取project的信息
    def jsonStr = libraryResource "info/server.json"
    serversMap = new JsonSlurperClassic().parseText(jsonStr).get("servers")
    println serversMap
}

// docker镜像构建服务器
def getDockerBuildServer(deployEnv) {
    return serversMap.get("dockerBuildServer").get(deployEnv)
}

// docker仓库信息
def getDockerRegister(deployEnv) {
    return serversMap.get("dockerRegister").get(deployEnv)
}

// k8s信息
def getK8s(deployEnv) {
    return serversMap.get("k8s").get(deployEnv)
}

// JFrog Artifactory仓库
def getArtifactory() {
    return serversMap.get("artifactory").get("url")
}
