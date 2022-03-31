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
        stage('Setup') {
            tools {
                jdk "jdk-11.0.2"
                maven 'Maven 3.6.3' 
            }      
            steps {
                sh 'mvn versions:set -DnewVersion=${version}.${BUILD_ID}-SNAPSHOT'
            }
        }
        stage('Clean') {
            tools {
                jdk "jdk-11.0.2"
                maven 'Maven 3.6.3' 
            }      
            steps {
                sh 'mvn -B clean'
            }
        }
        stage('Build') {
            tools {
                jdk "jdk-11.0.2"
                maven 'Maven 3.6.3' 
            }      
            steps {
                sh 'mvn -B -DskipTests package'
            }
        }        
        stage('Armando carpetas') {
            steps {
                script{
                    if(fileExists("/scripts/")){
                        sh 'mv scripts/* .'
                    }
                }
                sh 'mv target/*.jar .'
                sh 'rm original-*.jar'
            }
        }         
    }
    post {
        success {
             archiveArtifacts artifacts: '*.jar, *.bat'
        }
    }
}