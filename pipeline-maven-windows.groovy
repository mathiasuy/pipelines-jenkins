pipeline {
    agent any
    options {
        skipStagesAfterUnstable()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '15'))    
    }    
    parameters {  
        //gitParameter(branchFilter: 'origin/(.*)', defaultValue: 'develop', name: 'BRANCH', type: 'PT_BRANCH_TAG')
        string(name: 'BRANCH', defaultValue: 'develop', description: '') 
        string(name: 'VERSION', defaultValue: '1.0.0', description: '') 
        choice(name: 'LOG_LEVEL', choices: ['ERROR', 'WARN', 'INFO', 'DEBUG'], description: '')
    }
    environment {
            name="${JOB_BASE_NAME}"
            route="c:/git"
    }    
    stages {
        stage('Wipe') {
            steps {
                cleanWs deleteDirs: true, notFailBuild: true
            }
        }        
        stage('Clone') {
            steps {
                git branch: '${BRANCH}',  credentialsId: 'mathiasbattistella',  url: 'git@bitbucket.org:proyecto_bitbucket/${JOB_BASE_NAME}.git'  
            }       
        }       
        stage('Configurando logs') {
            steps {
                script { 
                    if (env.BRANCH == 'master') {
                        sh 'echo ESTABLECIENDO LOGS EN NIVEL ERROR - Rama master seleccionada'
                        contentReplace( configs: [ fileContentReplaceConfig( configs: [ fileContentReplaceItemConfig( search: 'level="[a-zA-Z]*"', replace: 'level="ERROR"', matchCount: 0) ], fileEncoding: 'UTF-8', filePath: 'src/main/resources/log4j2.xml') ])                    
                    } else {
                        sh "echo ESTABLECIENDO LOGS EN ${LOG_LEVEL}"
                        contentReplace( configs: [ fileContentReplaceConfig( configs: [ fileContentReplaceItemConfig( search: 'level="[a-zA-Z]*"', replace: 'level="${LOG_LEVEL}"', matchCount: 0) ], fileEncoding: 'UTF-8', filePath: 'src/main/resources/log4j2.xml') ])                    
                    }
                }
            }
        }      
        stage('RemoteWipe') {
            steps {
                sh "ssh -C -i $path_ssh_pk_win10 $usuario_win10@$dominio_win10 \" IF exist ${route}/${name} ( cd c:/git & rmdir ${name} /s /q )\""
            }
        }       
        stage('Creating environment...') {
            steps {
                sh 'ssh -C -i $path_ssh_pk_win10 $usuario_win10@$dominio_win10 \"cd ${route} & mkdir ${name}\"'
                sh "scp -C -i $path_ssh_pk_win10 -r * $usuario_win10@$dominio_win10:${route}/${name}"
            }
        }            
        stage('Setup') {
            tools {
                jdk "jdk-11.0.2"
                maven 'Maven 3.6.3' 
            }              
            steps {
                sh 'ssh -C -i $path_ssh_pk_win10 $usuario_win10@$dominio_win10 \";cd ${route}/${name} & mvn versions:set -DnewVersion=${VERSION}.${BUILD_ID}\"'
            }
        }
        stage('Clean') {
            tools {
                jdk "jdk-11.0.2"
                maven 'Maven 3.6.3' 
            }              
            steps {
                sh 'ssh -C -i $path_ssh_pk_win10 $usuario_win10@$dominio_win10 \";cd ${route}/${name} & mvn clean\"'
            }
        }
        stage('Build') {
            tools {
                jdk "jdk-11.0.2"
                maven 'Maven 3.6.3' 
            }              
            steps {
                sh 'ssh -C -i $path_ssh_pk_win10 $usuario_win10@$dominio_win10 \";cd ${route}/${name} & mvn -B -DskipTests -Dfile.encoding=UTF-8  package\"'
            }
        }        
        stage('Armando carpetas') {
            steps {
                script { 
                        sh 'echo check si existe'
                        sh 'if [ exist -C -i $usuario_win10@$dominio_win10:${route}/${name}/target/*.exe ];then scp -C -i $path_ssh_pk_win10 -r $usuario_win10@$dominio_win10:${route}/${name}/target/*.exe . ; fi'
                        sh 'echo traer scripts'
                        sh 'scp -C -i $path_ssh_pk_win10 -r $usuario_win10@$dominio_win10:${route}/${name}/scripts/* .'
                        sh 'echo traer jars'
                        sh 'scp -C -i $path_ssh_pk_win10 -r $usuario_win10@$dominio_win10:${route}/${name}/target/*.jar .'
                        sh 'echo reorganizar carpetas'
                        sh 'mv scripts/* .'
                        sh 'echo eliminar sobras'
                        sh 'ssh -C -i $path_ssh_pk_win10 $usuario_win10@$dominio_win10 rmdir \\"${route}/${name}\\" /s /q'       
                        sh 'rm original-*.jar'
                }
            }
        }        
        stage('Etiquetando version') {
            steps {
                sh 'echo BUILD: ${BUILD_URL} COMMIT: ${GIT_COMMIT} BRANCH: ${BRANCH_NAME} JOB_URL: ${JOB_URL} > info.txt'
            }
        }            
    }
    post {
        success {
             archiveArtifacts artifacts: '*.jar, *.bat, info.txt, *.properties, *.exe'
        }
    }
}