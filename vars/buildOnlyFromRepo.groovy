def call(String gitUrl, String branch = 'main') {
    def detectedProject
    def podTemplate
    
    node('java') { // temporary node for detection
        stage('Checkout') {
            git branch: branch, url: gitUrl
        }
        
        detectedProject = detectProject()
        podTemplate = languageHandlers.getPodTemplate(detectedProject.language)
    }
    
    echo "Auto-detected: ${detectedProject.language} (${detectedProject.buildTool ?: 'default'})"
    echo "Using pod template: ${podTemplate}"
    
    node(podTemplate) {
        stage('Checkout') {
            git branch: branch, url: gitUrl
        }
        
        stage('Build') {
            container(podTemplate) {
                languageHandlers.buildProject(detectedProject.language, detectedProject.buildTool)
            }
        }
        
        stage('Cleanup') {
            cleanWs()
        }
    }
}

def detectProject() {
    if (fileExists('pom.xml')) {
        return [language: 'java', buildTool: 'maven']
    }
    if (fileExists('build.gradle') || fileExists('build.gradle.kts')) {
        return [language: 'java', buildTool: 'gradle']
    }
    if (fileExists('package.json')) {
        return [language: 'nodejs']
    }
    if (fileExists('requirements.txt') || fileExists('setup.py') || fileExists('pyproject.toml')) {
        return [language: 'python']
    }
    if (fileExists('go.mod') || fileExists('main.go')) {
        return [language: 'go']
    }
    if (fileExists('Dockerfile')) {
        return [language: 'docker']
    }
    return [language: 'java', buildTool: 'maven']
}