pipeline {
  agent {
    kubernetes {
      label 'directory-watcher-service'  // all your pods will be named with this prefix, followed by a unique id
      idleMinutes 5  // how long the pod will live after no jobs have run on it
      yamlFile 'build-pod.yaml'  // path to the pod definition relative to the root of our project 
      defaultContainer 'maven'  // define a default container if more than a few stages use it, will default to jnlp container
    }
  }
  stages {
    stage('Build') {
      steps {  // no container directive is needed as the maven container is the default
        sh "mvn clean package"   
      }
    }
    stage('Build Docker Image') {
      steps {
        withCredentials([
            usernamePassword(credentialsId: 'dockerio',
              usernameVariable: 'username',
              passwordVariable: 'password')
          ])
        container('docker') {  
          sh "docker build -t vmartinvega/directory-watcher-service:latest ."  // when we run in this step, we're running it via a shell on the docker build-pod container, 
          sh "docker login --username=$username --password=$password"        // which is just connecting to the host docker deaemon
          sh "docker push vmartinvega/directory-watcher-service:latest"        // which is just connecting to the host docker deaemon
        }
      }
    }
  }
}
