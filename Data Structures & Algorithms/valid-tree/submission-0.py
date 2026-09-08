class Solution:
    def validTree(self, n: int, edges: List[List[int]]) -> bool:
        if not n: return False

        adjc = {i:[] for i in range(n)}
        visited = set()

        for n1, n2 in edges:
            adjc[n1].append(n2)
            adjc[n2].append(n1)

        def dfs(i,prev):
            if i in visited:
                return False
            
            visited.add(i)
            
            for j in adjc[i]:
                if j == prev:
                    continue
                if not dfs(j,i): return False
            return True
        
        return dfs(0,-1) and n == len(visited)