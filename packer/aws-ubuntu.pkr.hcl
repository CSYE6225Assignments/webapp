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

  # Step 1: Setup system (install Java, etc.)
  provisioner "shell" {
    script = "${path.root}/scripts/setup-system.sh"
  }

  # Step 2: Copy application JAR
  provisioner "file" {
    source      = var.app_artifact_path
    destination = "/tmp/application.jar"
  }

  # Step 3: Setup application (create user, directories, move JAR)
  provisioner "shell" {
    script = "${path.root}/scripts/setup-application.sh"
  }

  # Step 4: Setup systemd service
  provisioner "shell" {
    script = "${path.root}/scripts/setup-service.sh"
  }

  # Step 5: Copy CloudWatch Agent configuration
  provisioner "file" {
    source      = "${path.root}/config/cloudwatch-config.json"
    destination = "/tmp/cloudwatch-config.json"
  }

  # Step 6: Install and configure CloudWatch Agent
  provisioner "shell" {
    script = "${path.root}/scripts/setup-cloudwatch.sh"
  }

  # Step 7: Cleanup
  provisioner "shell" {
    script = "${path.root}/scripts/cleanup.sh"
  }

  # Generate manifest with AMI details
  post-processor "manifest" {
    output     = "manifest.json"
    strip_path = true
  }
}