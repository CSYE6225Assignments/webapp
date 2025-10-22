variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "aws_profile" {
  type    = string
  default = "dev"
}

variable "subnet_id" {
  type    = string
  default = ""
}

variable "security_group_id" {
  type    = string
  default = ""
}

variable "source_ami_owner" {
  type    = string
  default = "099720109477" # Canonical (Ubuntu)
}

variable "source_ami_name" {
  type    = string
  default = "ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"
}

variable "ssh_username" {
  type    = string
  default = "ubuntu"
}

variable "instance_type" {
  type    = string
  default = "t3.small"
}

variable "volume_size" {
  type    = number
  default = 16
}

variable "ami_name_prefix" {
  type    = string
  default = "csye6225-app"
}

variable "ami_users" {
  type    = list(string)
  default = []
}

variable "app_artifact_path" {
  type    = string
  default = "../target/health-check-api-0.0.1-SNAPSHOT.jar"
}
