# Definition for a binary tree node.
# class TreeNode:
#     def __init__(self, val=0, left=None, right=None):
#         self.val = val
#         self.left = left
#         self.right = right

class Solution:
    def isSameTree(self, p: Optional[TreeNode], q: Optional[TreeNode]) -> bool:
        
        def recursive(t1,t2):
            if not t1 and not t2:
                return True
            if not t1 or not t2:
                return False

            if t1.val != t2.val:
                return False
            return recursive(t1.left,t2.left) and  recursive(t1.right,t2.right)
           
            
        
        return recursive(p,q)