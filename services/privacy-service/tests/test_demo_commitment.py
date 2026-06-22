import unittest

from app.main import (
    COMMITMENT_ENGINE_VERSION,
    COMMITMENT_SCHEME,
    CommitmentLeaf,
    build_demo_commitment,
    verify_demo_commitment,
)


class DemoCommitmentTest(unittest.TestCase):
    def test_builds_deterministic_root_and_valid_proofs(self):
        leaves = [
            CommitmentLeaf("D-QD-001", "hd-qingdao-001"),
            CommitmentLeaf("D-QD-002", "hd-qingdao-002"),
            CommitmentLeaf("D-QD-003", "hd-qingdao-003"),
        ]

        first = build_demo_commitment(leaves)
        second = build_demo_commitment(list(reversed(leaves)))

        self.assertEqual(first["scheme"], COMMITMENT_SCHEME)
        self.assertEqual(first["engineVersion"], COMMITMENT_ENGINE_VERSION)
        self.assertEqual(first["root"], second["root"])
        self.assertEqual(set(first["proofs"].keys()), {"D-QD-001", "D-QD-002", "D-QD-003"})

        for leaf in leaves:
            self.assertTrue(
                verify_demo_commitment(
                    key=leaf.key,
                    value=leaf.value,
                    proof=first["proofs"][leaf.key],
                    root=first["root"],
                )
            )

    def test_rejects_wrong_value_root_and_sibling(self):
        leaves = [
            CommitmentLeaf("D-WF-001", "hd-weifang-001"),
            CommitmentLeaf("D-WF-002", "hd-weifang-002"),
        ]
        commitment = build_demo_commitment(leaves)
        proof = commitment["proofs"]["D-WF-001"]

        self.assertFalse(
            verify_demo_commitment(
                key="D-WF-001",
                value="tampered-hd",
                proof=proof,
                root=commitment["root"],
            )
        )
        self.assertFalse(
            verify_demo_commitment(
                key="D-WF-001",
                value="hd-weifang-001",
                proof=proof,
                root="0" * 64,
            )
        )

        tampered_proof = {**proof, "siblings": [{"direction": "right", "hash": "f" * 64}]}
        self.assertFalse(
            verify_demo_commitment(
                key="D-WF-001",
                value="hd-weifang-001",
                proof=tampered_proof,
                root=commitment["root"],
            )
        )

    def test_empty_commitment_keeps_scheme_metadata(self):
        commitment = build_demo_commitment([])

        self.assertEqual(commitment["scheme"], COMMITMENT_SCHEME)
        self.assertEqual(commitment["engineVersion"], COMMITMENT_ENGINE_VERSION)
        self.assertEqual(commitment["root"], "")
        self.assertEqual(commitment["proofs"], {})
        self.assertEqual(commitment["leafHashes"], {})


if __name__ == "__main__":
    unittest.main()
