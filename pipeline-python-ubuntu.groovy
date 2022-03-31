pipeline {
    options {
        skipStagesAfterUnstable()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '15'))    
    }    
    parameters {  
        //gitParameter(branchFilter: 'origin/(.*)', defaultValue: 'develop', name: 'BRANCH', type: 'PT_BRANCH_TAG')
        string(name: 'BRANCH', defaultValue: 'develop', description: '') 
        string(name: 'VERSION', defaultValue: '1.0.0', description: '') 
        choice(name: 'LOG_LEVEL', choices: ['ERROR', 'INFO', 'DEBUG'], description: '')
    }
    agent {
        docker {
            image 'digi0ps/python-opencv-dlib:latest'
        }
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
        stage('InstallRequeriments') { // Install any dependencies you need to perform testing
            steps {
                sh '''
                        bash -c "pip install -r dependencias.txt"
                    '''
                sh '''
                        bash -c "pyinstaller --onefile -y  run_linux_testing.spec"
                    '''
                stash(name: 'compiled-results', includes: '*.py*')
                sh '''
                        bash rm -rf dist
                    '''
            }
        }
        stage('Deliver') { 
            environment { 
                VOLUME = '$(pwd)/sources:/src'
                IMAGE = 'cdrx/pyinstaller-linux:python3'
            }
            steps {
                dir(path: env.BUILD_ID) { 
                    unstash(name: 'compiled-results') 
                }
            }
            post {
                success {
                    archiveArtifacts {
                        pattern("**")
                        onlyIfSuccessful()
                    }                    
                }
            }
        }
    }
}
