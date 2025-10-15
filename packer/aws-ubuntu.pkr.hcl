# Test
packer {
  required_plugins {
    amazon = {
      version = ">= 1.2.8"
      source  = "github.com/hashicorp/amazon"
    }
  }
}

source "amazon-ebs" "ubuntu" {
  profile         = var.aws_profile
  region          = var.aws_region
  instance_type   = var.instance_type
  ssh_username    = var.ssh_username
  ami_name        = "${var.ami_name_prefix}-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"
  ami_description = "Custom AMI for CSYE6225 Application"
  ami_users       = var.ami_users

  # Pin to default VPC
  subnet_id          = var.subnet_id
  security_group_ids = [var.security_group_id]

  source_ami_filter {
    filters = {
      name                = var.source_ami_name
      root-device-type    = "ebs"
      virtualization-type = "hvm"
    }
    most_recent = true
    owners      = [var.source_ami_owner]
  }

  launch_block_device_mappings {
    device_name           = "/dev/sda1"
    volume_size           = var.volume_size
    volume_type           = "gp3"
    delete_on_termination = true
  }

  tags = {
    Name        = "${var.ami_name_prefix}-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"
    Environment = "dev"
    CreatedBy   = "Packer"
    OS          = "Ubuntu 24.04 LTS"
  }
}

build {
  name = "csye6225-ami"
  sources = [
    "source.amazon-ebs.ubuntu"
  ]

  provisioner "shell" {
    script = "${path.root}/scripts/setup-system.sh"
  }

  provisioner "shell" {
    script = "${path.root}/scripts/setup-database.sh"
    environment_vars = [
      "MYSQL_DATABASE=${var.mysql_database}",
      "MYSQL_USER=${var.mysql_user}",
      "MYSQL_PASSWORD=${var.mysql_password}"
    ]
  }

  provisioner "file" {
    source      = var.app_artifact_path
    destination = "/tmp/application.jar"
  }

  provisioner "shell" {
    script = "${path.root}/scripts/setup-application.sh"
    environment_vars = [
      "MYSQL_DATABASE=${var.mysql_database}",
      "MYSQL_USER=${var.mysql_user}",
      "MYSQL_PASSWORD=${var.mysql_password}"
    ]
  }

  provisioner "shell" {
    script = "${path.root}/scripts/setup-service.sh"
  }

  provisioner "shell" {
    script = "${path.root}/scripts/cleanup.sh"
  }

  post-processor "manifest" {
    output     = "manifest.json"
    strip_path = true
  }
}
