#!/bin/bash

set -e

date
whoami

src_image_name="Ubuntu-20.04"
dest_image_name="${src_image_name}-copy"

echo "find source image ID"
src_image_id=$(openstack image list -f csv | grep \"$src_image_name\", | cut -d '"' -f 2)

echo "download source image"
glance image-download --file /tmp/$src_image_name.qcow2 $src_image_id

echo "find old destination image ID"
dest_image_id=$(openstack image list -f csv | grep \"$dest_image_name\", | cut -d '"' -f 2)

echo "delete old destination image"
glance image-delete $dest_image_id

echo "upload the new image"
glance image-create --name $dest_image_name --disk-format qcow2 --container-format bare --file /tmp/$src_image_name.qcow2

echo "image copy done"

