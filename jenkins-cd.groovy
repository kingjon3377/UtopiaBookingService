properties([parameters([string(defaultValue: 'booking-0.0.1-SNAPSHOT.jar', description: 'Name of the artifact to be deployed', name: 'ArtifactName', trim: true), string(defaultValue: 'us-east-1', description: 'AWS region to deploy the stack in', name: 'AWSRegion', trim: true), string(defaultValue: 'booking-service-stack', description: 'Name to give the stack', name: 'StackName')])]);
node {
    stage("Source"){
        git branch: 'elk-monitoring', url: 'https://github.com/kingjon3377/UtopiaBookingService.git';
    }
    stage('Delete previous cloud formation stack') {
        sh label: 'Delete old cloud formation', script: "aws --region us-east-1 cloudformation delete-stack --stack-name ${params.StackName}"
        sh label: 'Wait for the deletion of cloud formation', script: "aws --region us-east-1 cloudformation wait stack-delete-complete --stack-name ${params.StackName} || true"
    }

    stage('Create parameters file') {
        sh label: 'Remove old parameters file', script: 'rm -f cloud-formation-params.json';
        withCredentials([string(credentialsId: 'TargetBucket', variable: 'TargetBucket'), string(credentialsId: 'IAMRole', variable: 'IAMRole'), string(credentialsId: 'EC2Key', variable: 'EC2Key'), string(credentialsId: 'KibanaHostname', variable: 'KibanaHostname'), string(credentialsId: 'ElasticSearchHostname', variable: 'ElasticSearchHostname')]) {
            writeFile encoding: 'UTF-8', file: 'cloud-formation-params.json', text: """[
  {
    "ParameterKey": "JarFileSource",
    "ParameterValue": "s3://${TargetBucket}/${params.ArtifactName}"
  },
  {
    "ParameterKey": "JarFileName",
    "ParameterValue": "${params.ArtifactName}"
  },
  {
    "ParameterKey": "IAMRoleName",
    "ParameterValue": "${IAMRole}"
  },
  {
    "ParameterKey": "EC2KeyName",
    "ParameterValue": "${EC2Key}"
  },
  {
    "ParameterKey": "ElasticSearchHostname",
    "ParameterValue": "${ElasticSearchHostname}"
  },
  {
    "ParameterKey": "KibanaHostname",
    "ParameterValue": "${KibanaHostname}"
  }
]"""
        }
    }

    stage('Create cloud-formation stack') {
        sh label: 'Create new Cloud Stack', script: "aws --region ${params.AWSRegion} cloudformation create-stack --stack-name ${params.StackName} --template-body file://cftemplate.json --parameters file://cloud-formation-params.json";
        sh label: 'Wait for the creation of the cloud formation', script: "aws --region ${params.AWSRegion} cloudformation wait stack-create-complete --stack-name ${params.StackName}";
        sh label: 'Get endpoint DNS', script: 'aws cloudformation describe-stacks --stack-name EC2stack | jq \'.Stacks | .[0] | .Outputs | .[] | .OutputValue\' | sed \'s@^"\\(.*\\)"@\\1@\' | tac | tr \'\\n\' : | sed \'s@:$@@\' > BookingHost.url';
        withCredentials([string(credentialsId: 'TargetBucket', variable: 'TargetBucket')]) {
            s3Upload bucket: "${TargetBucket}", file: 'BookingHost.url';
        }
    }
}
