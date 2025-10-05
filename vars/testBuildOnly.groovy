def call(String gitUrl, String branch = 'main') {
    echo "Testing buildOnly with gitUrl: ${gitUrl}, branch: ${branch}"
    
    node('java') {
        stage('Test Checkout') {
            git branch: branch, url: gitUrl
        }
        
        stage('Test Detection') {
            if (fileExists('requirements.txt')) {
                echo "Found Python project"
            } else if (fileExists('pom.xml')) {
                echo "Found Maven project"
            } else {
                echo "Unknown project type"
            }
        }
    }
}