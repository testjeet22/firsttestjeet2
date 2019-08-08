#!usr/bin/env groovy

/*****************************************************************************************************************************
 *
 * © RBC CONFIDENTIAL
 *___________________
 *
 * This file provides all utility functions related to maven builds to be used by piepline scripts.
 *
 * Authors: Harshavardhana Badakere
 *
 * Pre-Requisite: Requires pipeline environment variables to be set by calling commonUtils.initializePipelineEnv()
 * 				  before using any of the functions in this class as these functions refer to env variables
 *
 * Contact: Digital-DevOps <digitaldevops@rbc.com>
 *
 **/

package utils

import groovy.util.*

/*
 * 	Method			: getMavenParams
 *
 * 	Purpose			: Extracts maven information like group ID, artifact ID etc.
 *
 * 	Pre-condition	: Assumes the function is run from the directory where the pom file exists
 *
 * 	Post-condition	: None
 *
 *  Parameters		: None
 *
 *  Returns			: List of extracted maven params as a key/value pair or null
 */
def getMavenParams() {

	def mavenParams = [:]
		
	def rootPomObj = readMavenPom (file: 'pom.xml')
	mavenParams['groupId'] = rootPomObj.getGroupId()
	mavenParams['version'] = rootPomObj.getVersion()

	//Check if it is a springboot project and load artifactId accordingly
	if (findFiles (glob: '**/application.yml').size() > 0 ) {
		mavenParams['artifactId'] = rootPomObj.getArtifactId()
	}
	else {
		//For non springboot projects locate the bluemix assembly file
		def searchFilePath = findFiles (glob: '**/bluemix-assembly.xml,**/ups-assembly.xml')
		if (searchFilePath.size() == 1) {
			//Extract the child directory path that conatins the bleumix assembly file from the search result above,
			//only if the bluemix assembly file is in a subdirectory in the project. This is needed in case of multi module projects.
			def pomFilePath = searchFilePath[0].path.contains("${env.PATH_SEPARATOR}") ?
									searchFilePath[0].path.substring(0,searchFilePath[0].path.lastIndexOf("${env.PATH_SEPARATOR}")) + "${env.PATH_SEPARATOR}" : ''
			mavenParams['artifactId'] = readMavenPom(file: pomFilePath + "pom.xml").getArtifactId()
		}
		else
		{
			println "Bluemix assembly file is missing or more than one such file found. Skipping the deployment"
			return null
		}
	}
	
	def deployUrl = getDeployUrlSnapshot('pom.xml')
	
	if (deployUrl) {
		//Extract the root folder name from the deploy url
		mavenParams ['deployFoldername'] = deployUrl.substring(deployUrl.lastIndexOf('/')+1)
	}
	else
		mavenParams ['deployFoldername'] = env.APP_CODE
		
	assert mavenParams['groupId'] != null:"groupId cannot be null"
	assert mavenParams['version']!= null:"version cannot be null"
	assert mavenParams['artifactId']!= null:"artifactId cannot be null"

	return mavenParams
}

/*
 * 	Method			: loadMavenMetadataXml
 *
 * 	Purpose			: Retrieves the nexus snapshot version by reading the maven-metadata xml file downloaded from nexus.
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: Maven parameters, Organization's App Code & project version
 *
 *  Returns			: XML file obejct
 */
def loadMavenMetadataXml(params, version) {

	def pomGroupId = params.groupId.replaceAll("${env.ESC_CHARACTER}.","${env.ESC_CHARACTER}${env.PATH_SEPARATOR}")
	
	def mavenMetadataFile = "${env.HOME_DIR_FOR_GROOVY}${env.PATH_SEPARATOR}" +
							".m2${env.PATH_SEPARATOR}" +
							"repository${env.PATH_SEPARATOR}" +
							"$pomGroupId${env.PATH_SEPARATOR}" +
							"$params.artifactId${env.PATH_SEPARATOR}" +
							"$version${env.PATH_SEPARATOR}" +
							"maven-metadata-${params.deployFoldername}.xml"
	
	if (fileExists (mavenMetadataFile))
		return readFile(file: mavenMetadataFile)
	else
		return null
}

/*
 * 	Method			: extractArtifactVersion
 *
 * 	Purpose			: Retrieves the artifact ID from the XML Object. Uses non serializable groovy methods.
 * 					  Hence the @NonCPS notation used.
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: Maven XML file object
 *
 *  Returns			: Artifact Version
 */

@NonCPS
def extractArtifactVersion(mavenMetadataObj) {

	//Convert string to Xml object
	def xmlObj = new XmlSlurper().parseText(mavenMetadataObj)
	def nexusArtifactVersion = xmlObj.'**'.find { node->
		node.name() == 'snapshotVersion' }.value.text()

	return nexusArtifactVersion
}

/*
 * 	Method			: checkJacoco
 *
 * 	Purpose			: Checks if the jacoco is supported for the given project.
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: None
 *
 *  Returns			: true or false
 */
def checkJacoco() {

	fileJacoco = findFiles(glob: 'jacoco.exec,**/jacoco.exec')
	if (fileJacoco.size() > 0)
			return true

	return false
}

/*
 * 	Method			: retrieveExclusions
 *
 * 	Purpose			: Retrieves the Jacoco Exclusions if any defined for the given project
 * 					  In case of multi module projects checks the pom.xml files recursively 
 * 					  for any jacoco exclusion blocks and loads them into a list.
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: Path to pom file, List of exclusions (Empty when passed)
 *
 *  Returns			: None
 */
def retrieveExclusions(pomPath, exclusionList) {

	pomData = readMavenPom(file: pomPath)

	//Check for Jacoco plugin in <plugins> section of the POM
	if (pomData.getBuild() != null) {
		if (pomData.getBuild().getPluginManagement() != null)
		getExclusion(pomData.getBuild().getPluginManagement(), exclusionList)
		else
		getExclusion(pomData.getBuild(), exclusionList)
	}

	//Check for child poms in the parent POM
	moduleList = pomData.getModules()

	/*Check for any Jacoco exclusions if moduleList is not null.
	 Makes a recursive function call for the current function itself
	 */
	if (moduleList != null) {
		for (String module: moduleList) {
			if (!(module.contains('pom.xml')))
			module = module + '/pom.xml'
			retrieveExclusions(module, exclusionList)
		}
	}
}

/*
 * 	Method			: getExclusion
 *
 * 	Purpose			: Extracts the exclusion if found in any of the POM file in to an array.
 *
* 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: Path to pom file, List of exclusions
 *
 *  Returns			: None
 */
def getExclusion(pomObj, exclusionList) {
	
	// Get the list of plugins in the POM
	pluginList = pomObj.getPlugins()

	//Check for Jacoco plugin if pluginData is not null
	if (pluginList != null) {
		for (List plugin: pluginList) {
			//If Jacoco plugin exists loop though the block to retrieve all exclusion parameters
			if (plugin.toString().contains('jacoco-maven-plugin')) {
				pluginConfiguration = plugin.getConfiguration().toString().trim()

				//Loop through configuration block to extract all exclusion parameters
				int index = 0
				while (true) {
					index = pluginConfiguration.indexOf("<exclude>")
					if (index >= 0) {
						index = index + 9
						pluginConfiguration = pluginConfiguration.substring(index)
						exclusionList.add(pluginConfiguration.substring(0, pluginConfiguration.indexOf("</exclude>")))
					} else
					break
				}
				break
			}
		}
	}
}

/*
 * 	Method			: getDeployUrlSnapshot
 *
 * 	Purpose			: Extracts the maven deploy snapshot url by reading the <distributionManagement> tag in pom.xml file.
 * 					  If the <distributionManagement> section is not found in the given pom it also tries to search 
 * 					  for the same in its parent POM.
 *
 * 	Pre-condition	: Assumes env variables are set in the pipeline initialization stage. 
 *
 * 	Post-condition	: None
 *
 *  Parameters		: String containing the name & full path of the POM file to be parsed. 
 *  				  Can be a relative or absolute path.
 *
 *  Returns			: The url as a string (The trailing '/' is trimmed if exists)
 */

def getDeployUrlSnapshot (pomFile) {
	
	def mavenPom = readMavenPom(file: pomFile)
	def deployUrl
	
	if (mavenPom.getDistributionManagement()) {
		deployUrl = mavenPom.getDistributionManagement().getSnapshotRepository().getUrl().trim()
	}
	else if (mavenPom.getParent()){
		def parentPomGroupId = mavenPom.getParent().getGroupId().replaceAll("${env.ESC_CHARACTER}.","${env.ESC_CHARACTER}${env.PATH_SEPARATOR}")
		def parentPomArtifactId = mavenPom.getParent().getArtifactId()
		def parentPomVersion = mavenPom.getParent().getVersion()
		
		//Construct the path of parent POM file based on the above information
		def parentPomFile = "${env.HOME_DIR_FOR_GROOVY}${env.PATH_SEPARATOR}" + 
							".m2${env.PATH_SEPARATOR}" + 
							"repository${env.PATH_SEPARATOR}" + 
							"${parentPomGroupId}${env.PATH_SEPARATOR}" + 
							"${parentPomArtifactId}${env.PATH_SEPARATOR}" + 
							"${parentPomVersion}${env.PATH_SEPARATOR}" + 
							"${parentPomArtifactId}-${parentPomVersion}.pom"
		deployUrl = readMavenPom (file: parentPomFile).getDistributionManagement().getSnapshotRepository().getUrl().trim()
	}
	else {
		println "No Deployment information found in the given POM or it's parent"
		return null
	}
		
	//Trim any trailing '/' character
	if (deployUrl.endsWith('/')) {
		deployUrl = deployUrl.substring(0,deployUrl.length() - 1)
	}
	
	return deployUrl
}

return this