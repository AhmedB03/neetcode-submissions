"""
# Definition for a Node.
class Node:
    def __init__(self, val = 0, neighbors = None):
        self.val = val
        self.neighbors = neighbors if neighbors is not None else []
"""

class Solution:
    def cloneGraph(self, node: Optional['Node']) -> Optional['Node']:
        if not node:
            return None
        og_to_clone = {}

        def dfs(n):
            if n in og_to_clone:
                return og_to_clone[n]
            
            clone = Node(n.val)
            og_to_clone[n] = clone

            for nei in n.neighbors:
                clone.neighbors.append(dfs(nei))

            return clone
        
        
        
        

        return dfs(node)


