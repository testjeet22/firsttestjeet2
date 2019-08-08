pipeline {
    agent any
    tools {
       //maven 'maven-5'
        //java 'java'
    }
    stages {
        stage ('Initialize') {
            steps {
                sh '''
                    echo "PATH = ${PATH}"
                    echo "M2_HOME = ${M2_HOME}"
                '''
            }
        }

        stage ('Build') {
            steps {
                //sh '/opt/apache-maven/bin/mvn  install -f my-app/pom.xml' 
               build job: 'maven'
                
            }
            
        }
        
        stage('static code analysis'){
            steps{
           // sh '/opt/apache-maven/bin/mvn  verify sonar:sonar-f my-app/pom.xml '
            sh 'ls -l ' 
            } //for sonar
        }
        stage ('Invoke 1st job') {
            steps {
                 build job: 'maven'
                 //build job: 'new-pipline'
                //build "job: '1st', propagate: true, wait: true"
                
            }
            
        }
        stage ('Invoke 2nd job') { 
            steps {
                 build job: 'maven'
                  //build job: 'test'
                //build "job: '1st', propagate: true, wait: true"
                
            }
            
        }
        stage ('Deply ') {
            steps {
                //sh 'curl -u admin:admin -T target  "http://35.171.4.135:8081/artifactory/generic-local/"'
                sh 'cp /root/.jenkins/workspace/maven/target/myapp-3.5-SNAPSHOT.jar  /tmp'                
            }
         }
        stage('Results') {
            steps {
               // junit 'my-app/target/surefire-reports/TEST-*.xml'
                //archive 'my-app/target/*.jar'
                 sh 'ls -l /tmp'
                    }
        }
    }
}
