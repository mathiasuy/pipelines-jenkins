node {
    currentBuild.displayName = "#${BUILD_ID}"
}
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
        choice(name: 'LOG_LEVEL', choices: ['ERROR', 'INFO', 'DEBUG'], description: '')
        choice(name: 'PERIL', choices: ['testing', 'prod'], description: '')
    }
    environment {
            name="${JOB_BASE_NAME}"
            route="c:/git"
            scripts="env/Scripts"
        }    

    stages {
        stage('Wipe') {
            steps {
                cleanWs deleteDirs: true, notFailBuild: true
                sh 'python3 --version'
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
                        contentReplace( configs: [ fileContentReplaceConfig( configs: [ fileContentReplaceItemConfig( search: 'level=.*', replace: 'level=ERROR', matchCount: 0) ], fileEncoding: 'UTF-8', filePath: 'resources/logConfigExecutor.conf') ])
                    } else {
                        sh "echo ESTABLECIENDO LOGS EN ${LOG_LEVEL}"
                        contentReplace( configs: [ fileContentReplaceConfig( configs: [ fileContentReplaceItemConfig( search: 'level=.*', replace: 'level=${LOG_LEVEL}', matchCount: 0) ], fileEncoding: 'UTF-8', filePath: 'resources/logConfigExecutor.conf') ])
                    }
                }
            }
        }        
        stage('RemoteWipe') {
            steps {
                sh "ssh -C -i $path_ssh_pk_win10 $usuario_win10@$dominio_win10 \" IF exist ${route}/${name} ( cd c:/git & rmdir ${name} /s /q )\""
            }
        }       
        stage('Prepare environment...') {
            tools {
                jdk "jdk-11.0.2"
                maven 'Maven 3.6.3' 
            }         
            steps {
                sh "pwd"
                sh "ls"
                sh 'ssh -C -i $path_ssh_pk_win10 $usuario_win10@$dominio_win10 \"cd ${route} & mkdir ${name}\"'
                sh "scp -C -i $path_ssh_pk_win10 -r * $usuario_win10@$dominio_win10:${route}/${name}"
                sh 'ssh -C -i $path_ssh_pk_win10 $usuario_win10@$dominio_win10 \"cd ${route}/${name} & virtualenv env\"'
            }
        }
        stage('Setup') {
            tools {
                jdk "jdk-11.0.2"
                maven 'Maven 3.6.3' 
            }              
            steps {
                sh '''

                if [ -f \"dependencias.txt\" ] 
                then
                    ssh -C -i $path_ssh_pk_win10 $usuario_win10@$dominio_win10 \"cd ${route}/${name} & \\"${scripts}/pip\\" install -r dependencias.txt\"
                fi
                if [ -f "dependencias_win64.txt" ]
                then
                    ssh -C -i $path_ssh_pk_win10 $usuario_win10@$dominio_win10 \"cd ${route}/${name} & \\"${scripts}/pip\\" install -r dependencias_win64.txt\"
                fi
                '''
            }
        }        
        stage('Build') {
            tools {
                jdk "jdk-11.0.2"
                maven 'Maven 3.6.3' 
            }              
            steps {
                sh '''
                if [ ${PERFIL} = testing ]] 
                then
                    echo -o-o-o-o-o-o-o-o-o-o- PERFIL TESTING - SE MOSTRARA LOG -o-o-o-o-o-o-o-o-o-o-
                else
                    echo -o-o-o-o-o-o-o-o-o-o- PERFIL PRODUCCION - NO SE MOSTRARA LOG -o-o-o-o-o-o-o-o-o-o-
                fi
                if [ ${PERFIL} = testing ]; then
                    ssh -C -i $path_ssh_pk_win10 $usuario_win10@$dominio_win10 \"cd ${route}/${name} & \\"${scripts}/pyinstaller\\" --onefile run_windows_testing.spec\"
                else
                    ssh -C -i $path_ssh_pk_win10 $usuario_win10@$dominio_win10 \"cd ${route}/${name} & \\"${scripts}/pyinstaller\\" --onefile run_windows_prod.spec\"
                fi                
                '''
            }
        }               


        stage('Armando carpetas') {
            steps {
                cleanWs deleteDirs: true, notFailBuild: true
                sh 'scp -C -i $path_ssh_pk_win10 -r $usuario_win10@$dominio_win10:${route}/${name}/dist .'
                sh 'ssh -C -i $path_ssh_pk_win10 $usuario_win10@$dominio_win10 rmdir \\"${route}/${name}\\" /s /q'                
                sh 'mkdir  ${name}'                
                sh 'mv dist/${name}/**  ${name}'    
                sh 'rm -rf dist'                       
            }
        }        
        stage('Etiquetando versi�n') {
            steps {
                sh 'echo BUILD: ${BUILD_URL} COMMIT: ${GIT_COMMIT} BRANCH: ${BRANCH_NAME} JOB_URL: ${JOB_URL} > info.txt'
            }
        }            
    }
    post {
        success {
            archiveArtifacts '**'
        }
    }
}