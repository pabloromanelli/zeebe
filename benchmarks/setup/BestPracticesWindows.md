# Best Practices Windows

Running the benchmarks is possible with the help of the Windows Subsystem for Linux.

The setup changes slightly compared to the Linux setup.

These are the components to install on Windows:
* Docker

These are the components to install within the WSL:
* gcloud https://cloud.google.com/sdk/install?hl=de
* Kubectl https://kubernetes.io/de/docs/tasks/tools/install-kubectl/
* Helm 3.*  https://helm.sh/docs/intro/install/
* kubens/kubectx https://github.com/ahmetb/kubectx

When following the instructions, execute all commands that deal with Docker in a Windows shell, and exeucte all other commands in the WSL shell.
