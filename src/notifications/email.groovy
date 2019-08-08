#!usr/bin/env groovy

 /*****************************************************************************************************************************
 * 
 * © RBC CONFIDENTIAL
 *___________________
 *  
 * This file provides all functions related to email notifications used by piepline scripts.
 * 
 * Authors: Harshavardhana Badakere
 * 
 * Pre-Requisite: Requires pipeline environment variables to be set by calling commonUtils.initializePipelineEnv()
 * 				  before using any of the functions in this class as these functions refer to env variables
 *
 * Contact: Digital-DevOps <digitaldevops@rbc.com>
 *
 **/

package notifications

/*
 * 	Method			: sendNotificationToContributors
 *
 * 	Purpose			: Sends email notifications to the repo contributors about the update made to specific branch.
 *
 * 	Pre-condition	: Assumes the function is run a from Jenkins workspace.
 * 					  Assumes the SCM tool used is Git
 *
 * 	Post-condition	: None
 *
 *  Parameters		: URL of the repository
 *
 *  Returns			: none
 */
def sendNotificationToContributors() {

	def commitInfoStr
	
	//Get the list of contributors for the repo
	def gitCmdcontribList = env.SUPPRESS_TERMINAL_OUTPUT + 'git log --pretty=format:' + env.HTTP_ESC_CHARACTER + '%ae | sort | uniq'
	def contribListArr = "${env.TERMINAL}"(script: "$gitCmdcontribList", returnStdout: true).tokenize('\n')
	
	//Check if the array size is > 1 to avoid sending emails to the author if the repo has only one contributor
	if ( contribListArr.size() > 1 ) {
		//Get the Commiter name & message
		def gitCmdCommitInfo = env.SUPPRESS_TERMINAL_OUTPUT + 'git log --pretty=format:"Committed By: ' +
								env.HTTP_ESC_CHARACTER + '%an' + env.HTTP_ESC_CHARACTER +
								'%nMessage: ' + env.HTTP_ESC_CHARACTER + '%B" -n 1'
		commitInfoStr = "${env.TERMINAL}"(script: "$gitCmdCommitInfo", returnStdout: true)
		
		//Send email notifications to all in the contributors list
		emailext body: """<p>Hello Contributors,</p>
		<p>The ${env.BRANCH_NAME} branch for repo &QUOT;<a href='${env.GIT_URL}'>${env.GIT_URL}</a>&QUOT; has been updated and successfully built with the following change: </p>
		<pre>${commitInfoStr}</pre>
		<p>Build Logs: &QUOT;<a href='${env.BUILD_URL}'>${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>&QUOT;</p>
		<p>Please update your current working branches by pulling the latest from the ${env.BRANCH_NAME} branch.""", mimeType: 'text/html', replyTo: 'digitaldevops@rbc.com', subject: "${env.ORG_NAME} >> ${env.REPO_NAME} >> ${env.BRANCH_NAME} UPDATED", to: contribListArr.join(",")
	}
}

return this