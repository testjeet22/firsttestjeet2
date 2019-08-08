node {

CAMUNDA_URL=sh (script: 'cat sonar-project.properties|grep -i sonar.projectKey|cut -d"=" -f2',
 returnStdout: true,)
 CAMUNDA_ENV=sh (script: 'cat sonar-project.properties|grep -i sonar.projectName|cut -d"=" -f2',
 returnStdout: true,)
 USE_BASIC_AUTH=sh (script: 'cat sonar-project.properties|grep -i sonar.projectVersion|cut -d"=" -f2',
 returnStdout: true,)
 CAMUNDA_USERNAME=sh (script: 'cat sonar-project.properties|grep -i sonar.sources|cut -d"=" -f2',
 returnStdout: true,)
 CAMUNDA_PASSWORD=sh (script: 'cat sonar-project.properties|grep -i sonar.language|cut -d"=" -f2',
 returnStdout: true,)
 CAMUNDA_URL1=sh (script: 'cat sonar-project.properties|grep -i sonar.sourceEncoding|cut -d"=" -f2',
 returnStdout: true,)
parameters {
    string(name: 'CAMUNDA_URL', defaultValue: 'CAMUNDA-URL-GOES-HERE', description: 'URL of Camunda Instance.')
//    choice(name: 'CAMUNDA_ENV', choices: 'dev\naccept', description: 'What Config Environment Files should be used?')
//    booleanParam(name: 'USE_BASIC_AUTH', defaultValue: false, description: 'Check this box if you want to use Camunda Basic Auth.')
//    string(name: 'CAMUNDA_USERNAME', defaultValue: 'default_username', description: 'Camunda Basic Auth Username: Must not be empty if you use Basic Auth.')
//    password(name: 'CAMUNDA_PASSWORD', defaultValue: 'default_password', description: 'Camunda Basic Auth Password: Must not be empty if you use Basic Auth.  WARNING: Passwords are exposed in the console output of this build script!!')
 }
 stage ('Clone repository...') {
        checkout([$class: 'GitSCM',
branches: [[name: '*/master']],
doGenerateSubmoduleConfigurations: false,
extensions: [],
submoduleCfg: [],
userRemoteConfigs: [[url: 'https://github.com/testjeet22/firsttestjeet2.git/']]]
   	)
    }
//sonar.projectKey=java-sonar-runner-simple
    stage('Build') {
         sh '/opt/maven/bin/mvn  -B -f pom.xml clean install'
    //   echo "${CAMUNDA_URL}"
    //   echo "${CAMUNDA_ENV}"
    //   echo "${USE_BASIC_AUTH}"
    //   echo "${CAMUNDA_USERNAME}" 
   
    }
    stage('Test') {
        echo 'Building....'
    }
     stage ('Push to UCD...') {
       ([$class: 'UCDeployPublisher',
            siteName: 'UDD_PUB',
            component: [
                $class: 'com.urbancode.jenkins.plugins.ucdeploy.VersionHelper$VersionBlock',
                componentName: 'JPetStore-APP-2',
                createComponent: [
                    $class: 'com.urbancode.jenkins.plugins.ucdeploy.ComponentHelper$CreateComponentBlock'
                ],
                delivery: [
                    $class: 'com.urbancode.jenkins.plugins.ucdeploy.DeliveryHelper$Push',
                    pushVersion: '${BUILD_NUMBER}',
                    baseDir: '.',
                    fileIncludePatterns: '/var/lib/jenkins/workspace/3-pipe-jenkins/target/*.jar',
                    fileExcludePatterns: '',
                    pushProperties: 'jenkins.server=Local\njenkins.reviewed=false',
                    pushDescription: 'Pushed from Jenkins',
                    pushIncremental: false
                ]
            ]
        ])
   }
}

//pipeline {
//       agent any
//  parameters {
//    string(name: 'CAMUNDA_URL', defaultValue: 'CAMUNDA-URL-GOES-HERE', description: 'URL of Camunda Instance.')
//    choice(name: 'CAMUNDA_ENV', choices: 'dev\naccept', description: 'What Config Environment Files should be used?')
//    booleanParam(name: 'USE_BASIC_AUTH', defaultValue: false, description: 'Check this box if you want to use Camunda Basic Auth.')
//    string(name: 'CAMUNDA_USERNAME', defaultValue: 'default_username', description: 'Camunda Basic Auth Username: Must not be empty if you use Basic Auth.')
//    password(name: 'CAMUNDA_PASSWORD', defaultValue: 'default_password', description: 'Camunda Basic Auth Password: Must not be empty if you use Basic Auth.  WARNING: Passwords are exposed in the console output of this build script!!')
//  }
//      stages {
//          stage("clone") {
//              steps {
//            checkout scm
//          }
//        }
//        stage("build & SonarQube analysis") {
//              steps{
//            builddeploy()
//          }
//        }
//        stage('Deploy approval'){
//        steps{
//        depapp()
//   
//}
//}
//        stage("Quality Gate") {
//         steps {
//          quality()
//          }
//        }
//        
//      }
//    }
//
//def builddeploy(){
//withSonarQubeEnv('sonar-server') {
//              sh '/opt/maven/bin/mvn clean package sonar:sonar'
//            }
//}
//def depapp(){
// timeout(time: 7, unit: 'DAYS'){
// mail to: 'jeetendrak69@gmail.com', subject: "Please approve #${env.BUILD_NUMBER}", body: """
//See ${env.BUILD_URL}input/
//"""
//    input submitter: 'userId', message: 'Ready?'}
//}
//
//def quality()
//{timeout(time: 5, unit: 'MINUTES') {
//           waitForQualityGate abortPipeline: true}
//}
//node {
//    paramAValue = "paramAValue"
//    paramBValue = "paramBValue"
//    build job: 'downstream-freestyle', parameters: [[$class: 'StringParameterValue', name: 'ParamA', value: paramAValue], [$class: 'StringParameterValue', name: 'ParamB', value: paramBValue]]
//}

//// this stage is skipped due to the when expression, so nothing is printed
//    stage('three') {
//      when {
//        expression { myVar != 'hotness' }
//      }
//      steps {
//        echo "three: ${myVar}"
//      }
//    }
//  }
