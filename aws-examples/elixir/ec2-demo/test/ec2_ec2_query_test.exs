defmodule Ec2Ec2QueryTest do
  use ExUnit.Case, async: true

  alias Ec2Types.{
    DescribeSecurityGroupsOutput,
    DescribeVpcsOutput,
    RunInstancesInput,
    Tag,
    TagSpecification,
    Vpc
  }

  defp fixture(name) do
    Path.join([__DIR__, "fixtures", name])
    |> File.read!()
  end

  defp ok_response(body) do
    %RuntimeTypes.HttpResponse{status: 200, body: body, headers: []}
  end

  describe "decode_describe_vpcs_response/1" do
    test "parses vpcSet items into Vpc structs" do
      body = fixture("describe_vpcs_response.xml")

      assert {:ok, %DescribeVpcsOutput{vpcs: vpcs, next_token: nil}} =
               Ec2Ec2Query.decode_describe_vpcs_response(ok_response(body))

      assert length(vpcs) == 2

      assert %Vpc{
               vpc_id: "vpc-12345678",
               cidr_block: "10.0.0.0/16",
               state: :available,
               is_default: false
             } in vpcs

      assert %Vpc{
               vpc_id: "vpc-abcdef01",
               cidr_block: "172.31.0.0/16",
               state: :available,
               is_default: true
             } in vpcs
    end

    test "returns empty list when vpcSet has no items" do
      body = """
      <?xml version="1.0" encoding="UTF-8"?>
      <DescribeVpcsResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
        <requestId>test-request-id</requestId>
        <vpcSet/>
      </DescribeVpcsResponse>
      """

      assert {:ok, %DescribeVpcsOutput{vpcs: [], next_token: nil}} =
               Ec2Ec2Query.decode_describe_vpcs_response(ok_response(body))
    end
  end

  describe "decode_describe_security_groups_response/1" do
    test "parses securityGroupInfo items into SecurityGroup structs" do
      body = fixture("describe_security_groups_response.xml")

      assert {:ok, %DescribeSecurityGroupsOutput{security_groups: [sg | _], next_token: nil}} =
               Ec2Ec2Query.decode_describe_security_groups_response(ok_response(body))

      assert sg.group_id == "sg-12345678"
      assert sg.group_name == "default"
      assert sg.description == "default VPC security group"
    end
  end

  describe "encode_run_instances_request/1" do
    test "includes tag specification members in query body" do
      input = %RunInstancesInput{
        image_id: "ami-12345678",
        instance_type: :t2_micro,
        min_count: 1,
        max_count: 1,
        tag_specifications: [
          %TagSpecification{
            resource_type: :instance,
            tags: [%Tag{key: "Name", value: "ec2-demo-instance"}]
          }
        ]
      }

      req = Ec2Ec2Query.encode_run_instances_request(input)

      assert req.method == "POST"
      assert req.body =~ "Action=RunInstances"
      assert req.body =~ "TagSpecification.1.ResourceType=instance"
      assert req.body =~ "TagSpecification.1.Tag.1.Key=Name"
      assert req.body =~ "TagSpecification.1.Tag.1.Value=ec2-demo-instance"
    end
  end
end
