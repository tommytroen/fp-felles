@Library('vl-jenkins')_

import no.nav.jenkins.*

node('DOCKER') {
    Date date= new Date()	
    def tagName
    maven = new maven()
    
    stage('Checkout scm') { // checkout only tags.
        checkout scm
	GIT_COMMIT_HASH = sh (script: "git log -n 1 --pretty=format:'%h'", returnStdout: true)
	changelist = "_" + date.format("YYYYMMDDHHmmss") + "_" + GIT_COMMIT_HASH
	mRevision = maven.revision()
	tagName = mRevision + changelist
        echo "Tag to be deployed $tagName"
    }
   
    stage('Build') {
        configFileProvider(
           [configFile(fileId: 'navMavenSettings', variable: 'MAVEN_SETTINGS')]) {
				
	        buildEnvironment = new buildEnvironment()
                if(maven.javaVersion() != null) {
                    buildEnvironment.overrideJDK(maven.javaVersion())
                }
				
                sh "mvn -U -B -s $MAVEN_SETTINGS -Dfile.encoding=UTF-8 -DdeployAtEnd=true -Dsha1= -Dchangelist= -Drevision=$tagName clean deploy"
				
           }   
                
    }
    stage('Create Github tag') {
        sh "git tag $tagName -m $tagName"
        sh "git push origin --tag"
    }
}

