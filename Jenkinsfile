// ============================================================
// Jenkinsfile — Java Energy Analyzer CI/CD Pipeline
//
// Declarative Pipeline for Jenkins.
//
// Pipeline stages:
//   1. Checkout
//   2. Build (Maven)
//   3. Unit Tests
//   4. Energy Analysis (quality gate)
//   5. Build Docker Image (optional)
//   6. Deploy (optional)
//
// Configure Jenkins with:
//   - JDK 17 tool named "JDK-17"
//   - Maven tool named "Maven-3.9"
//   - Environment variable ENERGY_ANALYZER_JAR pointing to the JAR path
// ============================================================

pipeline {
    agent any

    tools {
        jdk 'JDK-17'
        maven 'Maven-3.9'
    }

    environment {
        PROJECT_NAME    = "${env.JOB_NAME}"
        ANALYZER_JAR    = "${WORKSPACE}/energy-analyzer/target/java-energy-analyzer-1.0.0.jar"
        REPORT_DIR      = "${WORKSPACE}/energy-reports"
        // Threshold overrides - can also be read from application.yml
        EEI_THRESHOLD   = "60"
        MAX_HOTSPOTS    = "5"
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timestamps()
    }

    stages {

        // ── Stage 1: Checkout ─────────────────────────────────────────────
        stage('Checkout') {
            steps {
                echo '📥 Checking out source code...'
                checkout scm

                // Also checkout the energy analyzer (if separate repo)
                // dir('energy-analyzer') {
                //     git url: 'https://github.com/your-org/java-energy-analyzer.git',
                //         branch: 'main'
                // }
            }
        }

        // ── Stage 2: Build ────────────────────────────────────────────────
        stage('Build') {
            steps {
                echo '🔨 Building project...'
                sh 'mvn clean compile -q'
            }
            post {
                failure {
                    echo '❌ Build compilation failed'
                }
            }
        }

        // ── Stage 3: Unit Tests ───────────────────────────────────────────
        stage('Unit Tests') {
            steps {
                echo '🧪 Running unit tests...'
                sh 'mvn test -q'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml',
                          allowEmptyResults: true
                }
                failure {
                    echo '❌ Tests failed'
                }
            }
        }

        // ── Stage 4: Energy Analysis (Quality Gate) ───────────────────────
        stage('⚡ Energy Analysis') {
            steps {
                echo '⚡ Running Java Energy Efficiency Analysis...'
                echo '   EEI Threshold: ' + env.EEI_THRESHOLD
                echo '   Max Hotspots:  ' + env.MAX_HOTSPOTS

                script {
                    // Create output directory
                    sh "mkdir -p ${env.REPORT_DIR}"

                    // Write threshold config for this run
                    writeFile file: "${env.WORKSPACE}/energy-thresholds.yml", text: """
energy-analyzer:
  thresholds:
    minimum-eei-threshold: ${env.EEI_THRESHOLD}
    critical-threshold: 30
    max-allowed-hotspots: ${env.MAX_HOTSPOTS}
    fail-build-on-violation: true
"""

                    // Run the energy analyzer in CLI mode
                    // Exit code: 0 = pass, 1 = threshold violation, 2 = error
                    def exitCode = sh(
                        script: """
                            java -jar ${env.ANALYZER_JAR} \
                                src/main/java/ \
                                --name="${env.PROJECT_NAME}" \
                                --spring.config.additional-location=file:${env.WORKSPACE}/energy-thresholds.yml \
                                2>&1 | tee ${env.REPORT_DIR}/energy-analysis.txt
                            exit \${PIPESTATUS[0]}
                        """,
                        returnStatus: true
                    )

                    // Archive the report regardless of outcome
                    archiveArtifacts artifacts: 'energy-reports/energy-analysis.txt',
                                     allowEmptyArchive: true

                    if (exitCode == 1) {
                        // Quality gate failure - mark build as unstable or failed
                        // Change to 'error' to hard-fail, 'unstable' to warn
                        unstable(message: '⚠️  Energy Efficiency quality gate FAILED. ' +
                                          'Review energy-reports/energy-analysis.txt for violations.')

                        // To hard-fail instead of unstable:
                        // error('❌ Energy Efficiency quality gate FAILED.')

                    } else if (exitCode == 2) {
                        unstable(message: '⚠️  Energy Analyzer encountered an error. Check logs.')
                    } else {
                        echo '✅ Energy Efficiency quality gate PASSED'
                    }
                }
            }

            post {
                always {
                    // Publish report as a Jenkins build artifact
                    publishHTML(target: [
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'energy-reports',
                        reportFiles: 'energy-analysis.txt',
                        reportName: 'Energy Analysis Report'
                    ])
                }
            }
        }

        // ── Stage 5: Package ──────────────────────────────────────────────
        stage('Package') {
            when {
                // Only package if energy analysis passed (build is not unstable)
                expression { currentBuild.currentResult == 'SUCCESS' }
            }
            steps {
                echo '📦 Packaging application...'
                sh 'mvn package -DskipTests -q'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        // ── Stage 6: Deploy (conditional) ─────────────────────────────────
        stage('Deploy to Staging') {
            when {
                allOf {
                    branch 'main'
                    expression { currentBuild.currentResult == 'SUCCESS' }
                }
            }
            steps {
                echo '🚀 Deploying to staging...'
                // sh 'kubectl apply -f k8s/staging/ ...'
                echo '   (Configure your deployment steps here)'
            }
        }
    }

    // ── Post-Pipeline Actions ──────────────────────────────────────────────
    post {
        always {
            echo "📊 Pipeline complete. Status: ${currentBuild.currentResult}"
        }
        success {
            echo '✅ Pipeline PASSED — all stages including energy quality gate.'
        }
        unstable {
            echo '⚠️  Pipeline UNSTABLE — energy quality gate has violations.'
            // Optionally notify Slack/Teams:
            // slackSend color: 'warning',
            //   message: "Energy gate violated in ${env.JOB_NAME} #${env.BUILD_NUMBER}"
        }
        failure {
            echo '❌ Pipeline FAILED.'
        }
        cleanup {
            cleanWs()
        }
    }
}
