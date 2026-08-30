# Definition for a binary tree node.
# class TreeNode:
#     def __init__(self, val=0, left=None, right=None):
#         self.val = val
#         self.left = left
#         self.right = right

class Solution:
    def buildTree(self, preorder: List[int], inorder: List[int]) -> Optional[TreeNode]:
        inorder_map = {val: idx for idx, val in enumerate(inorder)}
        
        # Track the current root position in preorder list
        pre_idx = 0
        
        def helper(in_left, in_right):
            nonlocal pre_idx
            
            # Base case: no elements left to build this subtree
            if in_left > in_right:
                return None
            
            # Select the current root value and increment the preorder tracker
            root_val = preorder[pre_idx]
            root = TreeNode(root_val)
            pre_idx += 1
            
            # Get the split point in the inorder list
            mid = inorder_map[root_val]
            
            # Build left and right subtrees based on boundaries
            root.left = helper(in_left, mid - 1)
            root.right = helper(mid + 1, in_right)
            
            return root
            
        return helper(0, len(inorder) - 1)