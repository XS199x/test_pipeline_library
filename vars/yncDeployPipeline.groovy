/*
 * 元年云，部署
 */
def call(dockerBuildServer, k8s, project, projectCodes, ecsBranch) {
    pipeline {
        agent { node { label 'npm' } }

   parameters {
        choice(name: 'projectCode', choices: projectCodes, description: '请选择项目')
    }
        environment {
            ARTIFACTORY_CREDS = credentials('48-96-artifactory')
            String deployEnv = 'utest'
            String tag = sh(returnStdout: true, script: 'date +%Y%m%d%H%M%S').trim()
            String k8sApiVersion = 'apps/v1beta1'
            //当前选择项目的name
            String projectName = ''
            //当前选择项目的url
            String projectUrl = ''
            //当前选择项目的port
            String projectPort = ''
            //当前选择项目的type
            String projectType = ''
            //当前选择项目的k8s中deployment名字
            String k8sName = ''
            //当前选择项目的k8s中部署的namespace
            String k8sNamespace = ''
            //要求所有tag必须由英文前缀加数字后缀组成,branch对应tag的前缀，比如dev,utest,release, hotfix。Artifactory存储会用到
            String branch = ''
            // Artifactory repo
            String artifactoryRepo = ''
            //docker镜像构建服务器的jenkins凭据id
            String dockerBuildServerCredentialsId = ''
            //docker register jenkins凭据id
            String dockerRegisterCredentialsId = ''
            //docker register的地址
            String dockerRegisterUrl = ''
            //docker register的端口
            String dockerRegisterPort = ''
            //docker register的用户
            String dockerRegisterUser = ''
            //docker register的密码
            String dockerRegisterPassword = ''
            //k8sApiUrl
            String k8sApiUrl = ''
            //k8s jenkins凭据id
            String k8sCredentialsId = ''
            //release环境不允许自动更新k8s
            boolean updateK8sFlag = true
            //前端的编译脚本中用到的环境参数
            String buildEnv = ''
        }

        stages {
            // 各种参数的赋值
            stage('Init Params') {
                steps {
                    buildName "${env.projectCode}"
                    buildDescription "Executed @ ${NODE_NAME}"

                    script {
                        // 获取当前选择项目的信息和服务器信息
                        println 'env.projectCode: ' + env.projectCode
                        println 'deployEnv: ' + deployEnv

                        projectMap = project.getProjectMap(env.projectCode)
                        projectName = projectMap.get('name')
                        projectUrl = projectMap.get('url')
                        projectPort = projectMap.get('port')
                        projectType = projectMap.get('type')
                        k8sName = projectMap.get('k8sName')
                        k8sNamespace = projectMap.get('k8sNamespace')
                        println 'projectName: ' + projectName
                        println 'projectUrl: ' + projectUrl
                        println 'projectPort: ' + projectPort
                        println 'projectType: ' + projectType
                        println 'k8sName: ' + k8sName
                        println 'k8sNamespace: ' + k8sNamespace

                        server.init()
                        artifactoryRepo = 'http://192.168.48.96:8081/artifactory/ecs_java_dev_bj/ync-hotfix'
                        println 'artifactoryRepo: ' + artifactoryRepo

                        dockerBuildServer = server.getDockerBuildServer(deployEnv)
                        dockerBuildServerCredentialsId = dockerBuildServer.get('credentialsId')
                        println 'dockerBuildServer: ' + dockerBuildServer
                        println 'dockerBuildServerCredentialsId: ' + dockerBuildServerCredentialsId

                        dockerRegister = server.getDockerRegister(deployEnv)
                        dockerRegisterCredentialsId = dockerRegister.get('credentialsId')
                        dockerRegisterUrl = dockerRegister.get('dockerRegisterUrl')
                        dockerRegisterPort = dockerRegister.get('dockerRegisterPort')
                        println 'dockerRegister: ' + dockerRegister
                        println 'dockerRegisterCredentialsId: ' + dockerRegisterCredentialsId
                        println 'dockerRegisterUrl: ' + dockerRegisterUrl
                        println 'dockerRegisterPort: ' + dockerRegisterPort

                        k8s = server.getK8s(deployEnv)
                        k8sApiUrl = k8s.get('k8sApiUrl')
                        k8sCredentialsId = k8s.get('credentialsId')
                        updateK8sFlag = deployEnv == 'release' ? false : true
                        if (deployEnv == 'utest') {
                            buildEnv = 'test'
                    } else if (deployEnv == 'release') {
                            buildEnv = 'prod'
                    } else {
                            buildEnv = 'dev'
                        }
                        println 'k8sPhrase: ' + k8s
                        println 'k8sApiUrl: ' + k8sApiUrl
                        println 'buildEnv: ' + buildEnv
                    }
                }
            }

            // 不同的环境复制不同的配置文件，制作镜像，上传docker仓库
            stage('Docker') {
                steps {
                    //sh './gradlew test -PNEXUS_USERNAME=${NUSER} -PNEXUS_PASSWORD=${NPASS}'
                    withCredentials([usernamePassword(credentialsId: dockerBuildServerCredentialsId, usernameVariable: 'builderUsername', passwordVariable: 'builderPassword'),
                                 usernamePassword(credentialsId: dockerRegisterCredentialsId, usernameVariable: 'dockerUsername', passwordVariable: 'dockerPassword')]) {
                        script {
                            // docker register的口令
                            dockerRegisterUser = dockerUsername
                            dockerRegisterPassword = dockerPassword

                            // docker镜像构建服务器的口令
                            dockerBuildServer.user = builderUsername
                            dockerBuildServer.password = builderPassword
                            println(dockerBuildServer.user)
                            println(dockerBuildServer.password)
                            //构建docker镜像和推送的命令
                            cmdStr = "sed -i 's/#/$projectName/g' /data/server/${env.projectCode}/DockerFile;" +
                            "dos2unix /data/server/${env.projectCode}/*.sh;" +
                            "chmod -R 775 /data/server;/data/server/${env.projectCode}/build_images.sh $projectName $dockerRegisterUrl $dockerRegisterPort ${tag} $dockerRegisterUser $dockerRegisterPassword"
                        }

                        //从JFrog Artifactory下载编译后文件和不同type对应的构建docker需要的相关文件,组装
                        dir(env.projectCode) {
                            sh """
                            pwd
                            rm -rf *.zip
                            rm -rf *.war
                            rm -rf war
                            rm -rf sql
                            curl -u $ARTIFACTORY_CREDS -O $artifactoryRepo/ync-${ecsBranch}.zip
                            unzip -q ync-${ecsBranch}.zip
                            if [ -d "war" ];then
                               mv war/* .
                            fi
                            curl -u $ARTIFACTORY_CREDS -O $artifactoryRepo/conf/${ecsBranch}/build-conf/$projectType/${deployEnv}/docker-files.zip
                            """
                            //docoker镜像构建服务器上，清理文件夹，创建文件夹，远程复制，解压文件
                            sshCommand remote: dockerBuildServer, command: "rm -rf /data/server/${env.projectCode}/; mkdir -p /data/server/${env.projectCode}"
                            sshPut remote: dockerBuildServer, from: "${projectName}.war", into: "/data/server/${env.projectCode}/"
                            sshPut remote: dockerBuildServer, from: 'docker-files.zip', into: "/data/server/${env.projectCode}/"

                            sshCommand remote: dockerBuildServer, command: "cd /data/server/${env.projectCode}/;" +
                            "unzip -q ${projectName}.war -d ${projectName}; rm -rf ${projectName}.war;" +
                            "unzip -q docker-files.zip; rm -rf docker-files.zip;" +
                            /**
                            ecs-console项目的platformConfig.js和ecs-console-mobile项目的globaleConfig.js
                              targetUrl: '//testapp.yuanian.com/ecs-cloudplatform-api/index.html#/', // 跳转地址（多租户登录中心）
                              isPlatform: true, // 默认false, 多租户部署时改为true

                            ecs-console-mobile项目的globaleConfig.js
                              AintentUrl:'https://intent.yuanian.com/intent',//Ai语言识别地址，将录音汉字转成支出记录json值返回
                              AinvoiceUrl:'https://yuyin.yuanian.com/yuyin',//Ai语言识别地址,将录音base64转成汉字
                            fssc-mobile下的globalconfig.js
                              isShowWxCard:'true',  //由false改成true
                            */
                            "cd ${projectName} ;" +
                            '''
                            find ./ -name platformConfig.js -exec cat {} +
                            find ./ -name globalConfig.js -exec cat {} +
                            find ./ -name platformConfig.js -exec sed -i 's@targetUrl:.*$@targetUrl:\\x27//testapp.yuanian.com/ecs-cloudplatform-api/index.html#/\\x27,@g' '{}' ';'
                            find ./ -name platformConfig.js -exec sed -i 's/isPlatform:.*$/isPlatform: true,/g' '{}' ';' 
                            find ./ -name globalConfig.js -exec sed -i 's@targetUrl:.*$@targetUrl:\\x27//testapp.yuanian.com/ecs-cloudplatform-api/index.html#/\\x27,@g' '{}' ';'
                            find ./ -name globalConfig.js -exec sed -i 's/isPlatform:.*$/isPlatform: true,/g' '{}' ';' 
                            find ./ -name globalConfig.js -exec sed -i 's@AintenUrl:.*$@AintentUrl:\\x27https://intent.yuanian.com/intent\\x27,@g' '{}' ';'
                            find ./ -name globalConfig.js -exec sed -i 's@AinvoiceUrl:.*$@AinvoiceUrl:\\x27https://yuyin.yuanian.com/yuyin\\x27,@g' '{}' ';'
                            find ./ -name globalConfig.js -exec sed -i 's/isShowWxCard:.*$/isShowWxCard:\\x27true\\x27,/g' '{}' ';' 
                            find ./ -name platformConfig.js -exec cat {} +
                            find ./ -name globalConfig.js -exec cat {} +
                            '''

                            //组装后的成品，ssh上传到docker镜像构建服务器，完成镜像构建和推送镜像仓库
                            sshCommand remote: dockerBuildServer, command: cmdStr
                        }
                    }
                }
            }

            // 更新k8s
            stage('Update K8S') {
                when {
                    expression {
                        //非release环境，才执行更新k8s
                        updateK8sFlag
                    }
                }
                environment {
                    def updateJson = ''
                }
                steps {
                    script {
                        //如果docker镜像仓库port为0则忽略端口
                        if (dockerRegisterPort == '0') {
                            updateJson = '{"spec":{"template":{"spec":{"containers":[{"name":"' + k8sName + '","image":"' + dockerRegisterUrl + '/' + projectName + ':' + tag + '"}]}}}}'
                    } else {
                            updateJson = '{"spec":{"template":{"spec":{"containers":[{"name":"' + k8sName + '","image":"' + dockerRegisterUrl + ':' + dockerRegisterPort + '/' + projectName + ':' + tag + '"}]}}}}'
                        }
                    }
                    withCredentials([string(credentialsId: k8sCredentialsId, variable: 'ks8ApiKey')]) {
                        sh """
                           curl -k -X PATCH -H "Authorization:Bearer $ks8ApiKey" \
                                -H "Content-Type: application/strategic-merge-patch+json" \
                                -d '$updateJson' \
                                $k8sApiUrl/apis/${k8sApiVersion}/namespaces/$k8sNamespace/deployments/$k8sName
                        """
                    }
                }
            }
        }
    }
}
