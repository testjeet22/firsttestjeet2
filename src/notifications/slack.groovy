#!usr/bin/env groovy

/*****************************************************************************************************************************
 * 
 * © RBC CONFIDENTIAL
 *___________________
 *  
 * This file provides all functions related to slack notifications used by piepline scripts.
 * 
 * Authors: Harshavardhana Badakere
 * 
 * Contact: Digital-DevOps <digitaldevops@rbc.com>
 *
 **/

package notifications

 /*
 * 	Method			: notifyBuildStatus
 *
 * 	Purpose			: Sends slack notifications to developer channels about build status. 
 * 					  Posts to a generic slack channel by deafult. 
 * 					  Team specific slack chnanels can be setup and configuiration can be passed in Jenkinsfile
 *
 * 	Pre-condition	: Assumes the slack channel is setup and conifured in Jenkins master. 
 * 					  
 * 	Post-condition	: None
 *
 *  Parameters		: Slack message to be posted
 *
 *  Returns			: none
 */
 def notifyBuildStatus(String buildStatus) {

	assert buildStatus != null: "Build Status cannot be null"
	
	def colorName
	def colorCode
	def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
	def summary = "${subject} (${env.BUILD_URL})"
	def details = """<p>STARTED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':</p>
	  			<p>Check console output at &QUOT;<a href='${env.BUILD_URL}'
				>${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>&QUOT;</p>"""

	// Set color values based on build status
	if (buildStatus == 'STARTED') {
		colorCode = '#FFFF00'
	} else if (buildStatus == 'SUCCESS') {
		colorCode = '#00FF00'
	} else {
		colorCode = '#FF0000'
	}

	//get Credential
	withCredentials([[$class: 'StringBinding', credentialsId: 'slack-ci-integrations' , variable: 'token']]) {
		// Send notifications to default developer channel
		slackSend (color: colorCode, channel: 'digital-dev-team', message: summary,
		teamDomain: 'ci-notifications', token: token)
	}
		
	// Send notifications to team specific developer channel if set
	if (binding.hasVariable('slackChannel')) {
		withCredentials([[$class: 'StringBinding', credentialsId: slackToken , variable: 'token']]) {
			slackSend (color: colorCode, channel: slackChannel, message: summary,
						teamDomain: slackDomain, token: token)
		}
	}
}

/*
 * 	Method			: notifyDeployStatus
 *
 * 	Purpose			: Sends slack notifications to UCD team's channel. 
 * 					  Trigered everytime a deployment fails
 *
 * 	Pre-condition	: Assumes the slack channel is setup and conifured in Jenkins master. 
 * 					  
 * 	Post-condition	: None
 *
 *  Parameters		: Slack message to be posted
 *
 *  Returns			: none
 */
def notifyDeployStatus(postMsg) {

	assert postMsg != null: "Deployment Status cannot be null"
	
	withCredentials([[$class: 'StringBinding', credentialsId: 'slack-ucd' , variable: 'token']]) {
		slackSend (color: '#FF0000', channel: 'jenkins', message: postMsg, teamDomain: 'digitalqe', token: token)
	}
}

return this
