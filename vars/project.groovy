import groovy.json.JsonSlurperClassic
import groovy.transform.Field

//map 项目编号与项目信息的map
@Field final projectCodeMap = [:]

// list 项目编码和名称
@Field final projectCodeList = []

def init(type) {
    // 获取project的信息
    def jsonStr = libraryResource "info/project.json"
    def projectJson = new JsonSlurperClassic().parseText(jsonStr)
    def projectArray = projectJson.get('projects')
    projectArray.each{
        if(it.get("type") == type ) {
            def code = it.get('code')
            def projectMap = it
            projectCodeMap.put(code, projectMap)
            projectCodeList.add(code)
        }
    }
}

def getProjectMap(code) {
    return projectCodeMap.get(code)
}

def getCodes() {
    projectCodeList
}