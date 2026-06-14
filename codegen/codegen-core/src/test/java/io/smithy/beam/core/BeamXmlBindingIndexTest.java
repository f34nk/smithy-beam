package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ShapeId;

import static org.assertj.core.api.Assertions.assertThat;

class BeamXmlBindingIndexTest {

    private static final String MODEL = """
            $version: "2"
            namespace smithy.beam.test.restxmllists

            use aws.protocols#restXml
            use smithy.api#xmlFlattened
            use smithy.api#xmlName

            @restXml
            service RestXmlListService {
                version: "2026"
                operations: [GetItems]
            }

            operation GetItems {
                input: Unit
                output: GetItemsOutput
            }

            structure Unit {}

            structure GetItemsOutput {
                @xmlFlattened
                contents: ObjectList

                buckets: BucketList
            }

            list ObjectList {
                member: ObjectItem
            }

            structure ObjectItem {
                key: String
            }

            list BucketList {
                member: BucketItem
            }

            apply BucketList$member @xmlName("Bucket")

            structure BucketItem {
                name: String
            }
            """;

    @Test
    void listItemElementNameUsesXmlNameOnListMember() {
        Model model = Model.assembler()
                .addUnparsedModel("test.smithy", MODEL)
                .discoverModels()
                .assemble()
                .unwrap();
        ListShape bucketList = model.expectShape(
                ShapeId.from("smithy.beam.test.restxmllists#BucketList"), ListShape.class);

        assertThat(BeamXmlBindingIndex.listItemElementName(bucketList)).isEqualTo("Bucket");
    }

    @Test
    void flattenedContainerMemberUsesContainerWireNameForListItems() {
        Model model = Model.assembler()
                .addUnparsedModel("test.smithy", MODEL)
                .discoverModels()
                .assemble()
                .unwrap();
        MemberShape contentsMember = model.expectShape(
                        ShapeId.from("smithy.beam.test.restxmllists#GetItemsOutput"), software.amazon.smithy.model.shapes.StructureShape.class)
                .getMember("contents")
                .orElseThrow();
        ListShape objectList = model.expectShape(contentsMember.getTarget(), ListShape.class);

        assertThat(BeamXmlBindingIndex.isContainerMemberFlattened(contentsMember)).isTrue();
        assertThat(BeamXmlBindingIndex.listItemElementName(contentsMember, objectList, model))
                .isEqualTo("Contents");
    }
}
